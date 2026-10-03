// Petit client HTTP : même origine, cookies de session, jeton CSRF sur les requêtes qui modifient.

export class ApiError extends Error {
  constructor(status, body) {
    super(`HTTP ${status}`)
    this.status = status
    this.body = body
  }
}

let unauthorizedHandler = null

/** Appelé quand une route /api/admin répond 401 (session expirée) : renvoie l'admin vers la connexion. */
export function onUnauthorized(handler) {
  unauthorizedHandler = handler
}

export function notifyUnauthorized() {
  unauthorizedHandler?.()
}

export function csrfToken() {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/)
  return match ? decodeURIComponent(match[1]) : null
}

/**
 * @param {string} path  ex. '/api/auth/me'
 * @param {{method?: string, body?: any, headers?: Record<string,string>}} [options]
 *   body : URLSearchParams / FormData envoyés tels quels, tout autre objet en JSON.
 */
export async function api(path, { method = 'GET', body, headers = {} } = {}) {
  const options = { method, headers: { Accept: 'application/json', ...headers }, credentials: 'same-origin' }

  if (method !== 'GET' && method !== 'HEAD') {
    const token = csrfToken()
    if (token) options.headers['X-XSRF-TOKEN'] = token
  }

  if (body instanceof URLSearchParams || body instanceof FormData) {
    options.body = body
  } else if (body !== undefined) {
    options.headers['Content-Type'] = 'application/json'
    options.body = JSON.stringify(body)
  }

  const response = await fetch(path, options)
  const isJson = (response.headers.get('content-type') || '').includes('application/json')
  const data = isJson ? await response.json() : null

  if (!response.ok) {
    if (response.status === 401 && path.startsWith('/api/admin')) notifyUnauthorized()
    throw new ApiError(response.status, data)
  }
  return data
}

/**
 * Envoie UN fichier (champ « file ») avec une vraie progression : fetch() ne sait pas mesurer l'envoi,
 * XMLHttpRequest oui. Résout avec la réponse JSON ; rejette avec une ApiError (réponse refusée),
 * une Error (réseau) ou une AbortError (annulé via le signal).
 * @param {string} path
 * @param {File} file
 * @param {{ onProgress?: (fraction: number) => void, signal?: AbortSignal }} [options]
 */
export function uploadFile(path, file, { onProgress, signal } = {}) {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) {
      reject(new DOMException('Annulé', 'AbortError'))
      return
    }
    const xhr = new XMLHttpRequest()
    xhr.open('POST', path)
    xhr.setRequestHeader('Accept', 'application/json')
    const token = csrfToken()
    if (token) xhr.setRequestHeader('X-XSRF-TOKEN', token)

    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) onProgress?.(event.loaded / event.total)
    }
    xhr.onload = () => {
      let data = null
      try {
        data = xhr.responseText ? JSON.parse(xhr.responseText) : null
      } catch {
        /* réponse non JSON (ex. page d'erreur d'un proxy) : pas de détail */
      }
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve(data)
      } else {
        if (xhr.status === 401 && path.startsWith('/api/admin')) unauthorizedHandler?.()
        reject(new ApiError(xhr.status, data))
      }
    }
    xhr.onerror = () => reject(new Error('Erreur réseau'))
    xhr.onabort = () => reject(new DOMException('Annulé', 'AbortError'))
    signal?.addEventListener('abort', () => xhr.abort(), { once: true })

    const form = new FormData()
    form.append('file', file)
    xhr.send(form)
  })
}
