import { ApiError, csrfToken, notifyUnauthorized } from './api'

// Envoi d'un fichier par morceaux, avec reprise, pour les connexions instables (4G / 5G, wifi d'hôtel, portail captif).
//
//  1. POST  /api/admin/galleries/{id}/uploads   -> { id, received, ... } (reprend l'envoi interrompu du même fichier)
//  2. PUT   /api/admin/uploads/{id}?offset=N    -> un morceau (octets bruts) ; 409 { received } si le décalage est faux
//  3. POST  /api/admin/uploads/{id}/complete    -> l'élément créé (idempotent : une réponse perdue ne crée pas de doublon)
//
// Principes : chaque requête est courte donc facile à réessayer ; après toute erreur on demande au serveur où il en est
// (le morceau a pu arriver sans que la réponse nous parvienne) ; les morceaux rétrécissent quand la connexion est mauvaise et
// regrossissent quand elle va mieux ; hors connexion on attend le retour du réseau sans compter d'échec.

export const START_CHUNK = 2 * 1024 * 1024
export const MIN_CHUNK = 256 * 1024
export const MAX_CHUNK = 4 * 1024 * 1024
export const MAX_FAILURES = 10 // échecs consécutifs sans progrès avant d'abandonner (le serveur garde ce qui est reçu)
const BACKOFF_MS = [1000, 2000, 4000, 8000, 15000, 30000]
const CHUNK_STALL_MS = 45_000 // aucun octet envoyé pendant ce délai : connexion bloquée, on réessaie
const COMPLETE_STALL_MS = 10 * 60_000 // le traitement serveur d'une grosse vidéo peut durer
const MAX_RESTARTS = 2 // envoi à recommencer de zéro (le serveur a oublié l'envoi) au plus ce nombre de fois

/** Échec réseau (coupure, blocage, réponse qui n'est pas celle du serveur) : l'envoi peut être repris plus tard. */
export class NetworkError extends Error {
  constructor(message = 'Erreur réseau') {
    super(message)
    this.name = 'NetworkError'
  }
}

function abortError() {
  return new DOMException('Annulé', 'AbortError')
}

/**
 * Requête HTTP avec progression d'envoi et détection de blocage. Résout { status, data } (data : JSON ou null si la réponse n'est pas du JSON).
 * Rejette avec NetworkError (réseau, blocage) ou AbortError.
 */
export function xhrRequest(method, url, { body, json, onProgress, signal, stallMs = CHUNK_STALL_MS } = {}) {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) {
      reject(abortError())
      return
    }
    const xhr = new XMLHttpRequest()
    xhr.open(method, url)
    xhr.setRequestHeader('Accept', 'application/json')
    const token = csrfToken()
    if (token) xhr.setRequestHeader('X-XSRF-TOKEN', token)
    let payload = body
    if (json !== undefined) {
      xhr.setRequestHeader('Content-Type', 'application/json')
      payload = JSON.stringify(json)
    } else if (body) {
      xhr.setRequestHeader('Content-Type', 'application/octet-stream')
    }

    let stall = null
    let stalled = false
    const arm = () => {
      clearTimeout(stall)
      stall = setTimeout(() => {
        stalled = true // xhr.abort() déclenche « onabort » tout de suite : il faut savoir que ce n'est pas l'utilisateur
        xhr.abort()
      }, stallMs)
    }
    const done = () => clearTimeout(stall)
    arm()

    xhr.upload.onprogress = (event) => {
      arm()
      if (event.lengthComputable) onProgress?.(event.loaded)
    }
    xhr.upload.onload = arm // corps envoyé : on attend maintenant la réponse du serveur
    xhr.onload = () => {
      done()
      let data = null
      try {
        data = xhr.responseText ? JSON.parse(xhr.responseText) : null
      } catch {
        /* pas du JSON : page d'un proxy ou d'un portail captif */
      }
      resolve({ status: xhr.status, data })
    }
    xhr.onerror = () => {
      done()
      reject(new NetworkError())
    }
    xhr.ontimeout = xhr.onerror
    xhr.onabort = () => {
      done()
      reject(stalled ? new NetworkError('Connexion bloquée') : abortError())
    }
    signal?.addEventListener('abort', () => xhr.abort(), { once: true })
    xhr.send(payload)
  })
}

function sleep(ms, signal) {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) return reject(abortError())
    const timer = setTimeout(() => {
      signal?.removeEventListener('abort', onAbort)
      resolve()
    }, ms)
    const onAbort = () => {
      clearTimeout(timer)
      reject(abortError())
    }
    signal?.addEventListener('abort', onAbort, { once: true })
  })
}

/** Attend le retour du réseau (événement « online ») ; tout de suite si le navigateur se croit connecté. */
function waitOnline(signal) {
  return new Promise((resolve, reject) => {
    if (typeof navigator === 'undefined' || navigator.onLine !== false) return resolve()
    if (signal?.aborted) return reject(abortError())
    const onOnline = () => {
      signal?.removeEventListener('abort', onAbort)
      resolve()
    }
    const onAbort = () => {
      window.removeEventListener('online', onOnline)
      reject(abortError())
    }
    window.addEventListener('online', onOnline, { once: true })
    signal?.addEventListener('abort', onAbort, { once: true })
  })
}

/** Réessayer a un sens ? Réseau, serveur en difficulté (5xx), délai dépassé (408) ou trop de requêtes (429). */
function isRetryable(error) {
  if (error instanceof NetworkError) return true
  if (error instanceof ApiError) return error.status >= 500 || error.status === 408 || error.status === 429
  return false
}

/**
 * @param {number} galleryId
 * @param {File} file
 * @param {{ onProgress?: (fraction: number) => void, onNote?: (note: string) => void, signal?: AbortSignal,
 *           request?: typeof xhrRequest, wait?: (ms: number, signal?: AbortSignal) => Promise<void>,
 *           online?: (signal?: AbortSignal) => Promise<void> }} [options]
 *   request / wait / online : remplaçables dans les tests.
 * @returns {Promise<any>} l'élément créé
 */
export async function uploadResumable(galleryId, file, { onProgress, onNote, signal, request = xhrRequest, wait = sleep, online = waitOnline } = {}) {
  const note = (text) => onNote?.(text)

  /** Un appel au serveur : JSON en retour, erreurs converties (ApiError / NetworkError). */
  async function call(method, url, options = {}) {
    const { status, data } = await request(method, url, { signal, ...options })
    if (status >= 200 && status < 300) {
      if (data === null && options.expectJson !== false) throw new NetworkError('Réponse inattendue (portail du wifi ?)')
      return data
    }
    if (status === 401 && url.startsWith('/api/admin')) notifyUnauthorized()
    throw new ApiError(status, data)
  }

  /**
   * Attente avant un nouvel essai : hors connexion on attend le retour du réseau, sans compter d'échec ; sinon un délai croissant.
   * @returns {Promise<number>} le nombre d'échecs à retenir (inchangé, ou diminué d'un si l'échec n'était qu'une coupure du réseau)
   */
  async function backoff(failures, label) {
    let counted = failures
    if (typeof navigator !== 'undefined' && navigator.onLine === false) {
      note("Hors connexion : l'envoi reprendra tout seul au retour du réseau.")
      await online(signal)
      counted = Math.max(0, failures - 1)
    }
    if (counted > 0) {
      const delay = BACKOFF_MS[Math.min(counted - 1, BACKOFF_MS.length - 1)]
      note(`Connexion instable (${label}) : nouvel essai dans ${Math.round(delay / 1000)} s (${counted}/${MAX_FAILURES})…`)
      await wait(delay, signal)
    }
    note('')
    return counted
  }

  /** Réessaie `action` tant que l'échec est passager. Renvoie son résultat, ou lève l'erreur définitive. */
  async function attempt(action, label) {
    let failures = 0
    for (;;) {
      try {
        return await action()
      } catch (error) {
        if (error?.name === 'AbortError' || !isRetryable(error) || ++failures > MAX_FAILURES) throw error
        failures = await backoff(failures, label)
      }
    }
  }

  const size = file.size
  const fingerprint = `${file.name}|${file.size}|${file.lastModified}`

  const start = () =>
    attempt(() => call('POST', `/api/admin/galleries/${galleryId}/uploads`, { json: { filename: file.name, size, fingerprint } }), 'préparation')

  const hasOffset = (error) => error instanceof ApiError && error.status === 409 && typeof error.body?.received === 'number'

  let session = await start()
  let received = session.received
  let chunk = START_CHUNK
  let successes = 0
  let restarts = 0
  onProgress?.(received / size)

  for (;;) {
    let failures = 0
    let needSync = false // après un échec, le serveur a pu recevoir le morceau sans que la réponse nous arrive : on lui demande où il en est
    while (received < size) {
      try {
        if (needSync) {
          received = (await call('GET', `/api/admin/uploads/${session.id}`)).received
          needSync = false
          if (received >= size) break
        }
        const from = received
        const to = Math.min(size, from + chunk)
        const reply = await call('PUT', `/api/admin/uploads/${session.id}?offset=${from}`, {
          body: file.slice(from, to),
          onProgress: (sent) => onProgress?.(Math.min(0.999, (from + sent) / size)),
        })
        received = reply.received // la position annoncée par le serveur fait foi
        failures = 0
        onProgress?.(Math.min(0.999, received / size))
        if (++successes >= 3 && chunk < MAX_CHUNK) {
          chunk = Math.min(MAX_CHUNK, chunk * 2) // la connexion tient : morceaux plus gros
          successes = 0
        }
      } catch (error) {
        if (error?.name === 'AbortError') throw error
        if (hasOffset(error)) {
          received = error.body.received // le serveur en est ailleurs : on repart de sa position
          needSync = false
        } else if (error instanceof ApiError && error.status === 404 && restarts < MAX_RESTARTS) {
          restarts++ // le serveur a oublié cet envoi (nettoyage, redémarrage) : on recommence de zéro
          session = await start()
          received = session.received
          needSync = false
        } else if (isRetryable(error) && ++failures <= MAX_FAILURES) {
          chunk = Math.max(MIN_CHUNK, Math.floor(chunk / 2)) // connexion mauvaise : morceaux plus petits, donc plus faciles à faire passer
          successes = 0
          needSync = true
          failures = await backoff(failures, 'envoi')
        } else {
          throw error
        }
      }
    }

    try {
      note('')
      return await attempt(() => call('POST', `/api/admin/uploads/${session.id}/complete`, { stallMs: COMPLETE_STALL_MS }), 'validation')
    } catch (error) {
      if (hasOffset(error)) {
        received = error.body.received // il manque des octets : on les renvoie
      } else if (error instanceof ApiError && error.status === 404 && restarts < MAX_RESTARTS) {
        restarts++
        session = await start()
        received = session.received
      } else {
        throw error
      }
    }
  }
}
