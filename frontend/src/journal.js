import { reactive } from 'vue'
import { api } from './api'
import { todayIso } from './stageGeo'

// État partagé de l'écran principal : la liste (page courante) et la carte (toutes les galeries).
// La galerie sélectionnée n'est PAS ici : elle est dans l'adresse (/galerie/:id), seule source de vérité.
export const journal = reactive({
  data: null, // dernière page du fil : { content: [{ type: 'GALLERY', gallery } | { type: 'STAGE', stage }], page, pageSize, totalElements, totalPages }
  loading: false,
  error: false,
  galleries: [], // toutes les galeries (marqueurs), avec le numéro de page de chacune
  mapLoaded: false, // vrai une fois la réponse reçue (ou en échec) : la carte sait alors si elle a des galeries
  mapError: false,
  track: { segments: [], last: null }, // tracé de la route : lignes par source + dernière position connue
  upcomingDays: 7, // une étape à venir dans ce nombre de jours (ou moins) est montrée en tête du fil (réglage du serveur)
  today: todayIso(), // date du jour du serveur (remplace celle du navigateur dès qu'elle est connue) : une étape est « à venir » tant que son début la dépasse
  itineraryError: false,
  itineraryLoaded: false, // vrai une fois la réponse reçue (ou en échec) : sert au cadrage initial de la carte
  itinerary: [], // étapes du voyage dans l'ordre choisi par l'admin (lieux fixes et trajets), passées et à venir
})

let lastRequest = 0

export async function loadPage(page) {
  const request = ++lastRequest
  journal.loading = true
  journal.error = false
  try {
    const data = await api(`/api/public/feed?page=${page}`)
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

/**
 * L'itinéraire est un complément, comme le tracé : en cas d'échec on garde l'affichage précédent.
 * Chaque étape reçoit sa page dans le fil (`page`, null si elle est à venir ou si la position est inconnue).
 */
export async function loadItinerary() {
  try {
    const [stages, positions] = await Promise.all([
      api('/api/public/itinerary'),
      api('/api/public/feed/positions').catch(() => null),
    ])
    if (positions?.today) journal.today = positions.today
    if (Number.isInteger(positions?.upcomingDays)) journal.upcomingDays = positions.upcomingDays
    journal.itinerary = stages.map((stage) => ({ ...stage, page: positions?.stages?.[stage.id] ?? null }))
    journal.itineraryError = false
  } catch {
    journal.itineraryError = true /* réessayé à la prochaine visite de l'écran */
  } finally {
    journal.itineraryLoaded = true
  }
}
