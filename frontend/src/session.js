import { reactive } from 'vue'
import { api } from './api'

// État de la session admin, partagé par toute l'application (pas besoin de Pinia pour ça).
export const session = reactive({ loaded: false, authenticated: false, username: null })

export async function loadSession(force = false) {
  if (session.loaded && !force) return
  try {
    const data = await api('/api/auth/me')
    session.authenticated = Boolean(data.authenticated)
    session.username = data.username ?? null
  } catch {
    session.authenticated = false
    session.username = null
  }
  session.loaded = true
}

export async function login(username, password) {
  await loadSession() // garantit la présence du cookie CSRF
  await api('/api/auth/login', { method: 'POST', body: new URLSearchParams({ username, password }) })
  await loadSession(true)
}

export async function logout() {
  await api('/api/auth/logout', { method: 'POST' })
  session.authenticated = false
  session.username = null
}
