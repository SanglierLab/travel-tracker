import { reactive } from 'vue'
import { api } from './api'

// État partagé de l'écran principal : la liste (page courante) et la carte (toutes les galeries).
// La galerie sélectionnée n'est PAS ici : elle est dans l'adresse (/galerie/:id), seule source de vérité.
export const journal = reactive({
  data: null, // dernière page reçue : { content, page, pageSize, totalElements, totalPages }
  loading: false,
  error: false,
  galleries: [], // toutes les galeries (marqueurs), avec le numéro de page de chacune
  mapLoaded: false, // vrai une fois la réponse reçue (ou en échec) : la carte sait alors si elle a des galeries
  mapError: false,
  track: { segments: [], last: null }, // tracé de la route : lignes par source + dernière position connue
  itineraryLoaded: false, // vrai une fois la réponse reçue (ou en échec) : sert au cadrage initial de la carte
  itinerary: [], // étapes du voyage dans l'ordre choisi par l'admin (lieux fixes et trajets), passées et à venir
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
  } finally {
    journal.mapLoaded = true
  }
}

/** Le tracé est un complément : en cas d'échec on garde simplement l'affichage précédent. */
export async function loadTrack() {
  try {
    journal.track = await api('/api/public/track')
  } catch {
    /* réessayé automatiquement au prochain rafraîchissement */
  }
}

/** L'itinéraire est un complément, comme le tracé : en cas d'échec on garde l'affichage précédent. */
export async function loadItinerary() {
  try {
    journal.itinerary = await api('/api/public/itinerary')
  } catch {
    /* réessayé à la prochaine visite de l'écran */
  } finally {
    journal.itineraryLoaded = true
  }
}
