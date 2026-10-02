import { reactive } from 'vue'
import { api } from './api'

// État partagé de l'écran principal : la liste (page courante) et la carte (toutes les galeries).
// La galerie sélectionnée n'est PAS ici : elle est dans l'adresse (/galerie/:id), seule source de vérité.
export const journal = reactive({
  data: null, // dernière page reçue : { content, page, pageSize, totalElements, totalPages }
  loading: false,
  error: false,
  galleries: [], // toutes les galeries (marqueurs), avec le numéro de page de chacune
  mapError: false,
})

let lastRequest = 0

export async function loadPage(page) {
  const request = ++lastRequest
  journal.loading = true
  journal.error = false
  try {
    const data = await api(`/api/public/galleries?page=${page}`)
    if (request === lastRequest) journal.data = data
  } catch {
    if (request === lastRequest) journal.error = true
  } finally {
    // Une réponse plus récente a peut-être pris le relais : on ne touche pas à l'état dans ce cas.
    if (request === lastRequest) journal.loading = false
  }
}

export async function loadMap() {
  try {
    journal.galleries = await api('/api/public/map')
    journal.mapError = false
  } catch {
    journal.mapError = true
  }
}
