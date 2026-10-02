import { reactive } from 'vue'
import { api } from './api'

// État partagé de l'écran principal (la carte et la liste s'y brancheront toutes les deux).
export const journal = reactive({
  data: null, // dernière page reçue : { content, page, pageSize, totalElements, totalPages }
  loading: false,
  error: false,
  selectedId: null, // galerie mise en évidence
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
