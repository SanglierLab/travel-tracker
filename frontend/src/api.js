// Petit client HTTP : même origine, cookies de session, jeton CSRF sur les requêtes qui modifient.

export class ApiError extends Error {
  constructor(status, body) {
    super(`HTTP ${status}`)
    this.status = status
    this.body = body
  }
}

function csrfToken() {
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

  if (!response.ok) throw new ApiError(response.status, data)
  return data
}
