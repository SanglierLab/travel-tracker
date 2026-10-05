<script setup>
import { computed, defineAsyncComponent, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import GalleryCard from '../components/GalleryCard.vue'
import MediaViewer from '../components/MediaViewer.vue'
import Pagination from '../components/Pagination.vue'
import { journal, loadMap, loadPage, loadTrack } from '../journal'
import { TRACK_SOURCES } from '../trackStyle'

// Leaflet est chargé à part : la liste s'affiche sans attendre la bibliothèque de cartographie.
const TravelMap = defineAsyncComponent(() => import('../components/TravelMap.vue'))

const route = useRoute()
const router = useRouter()
const listElement = ref(null)

// L'adresse est l'unique source de vérité :
//   /?page=2          -> page 2, aucune galerie sélectionnée
//   /galerie/12       -> galerie 12 sélectionnée, sur la page où elle se trouve
//   ...?media=345     -> visionneuse ouverte sur le média 345 (le bouton « retour » la ferme)
const selectedId = computed(() => (route.name === 'gallery' ? Number(route.params.id) : null))
const queryPage = computed(() => {
  const n = parseInt(route.query.page, 10)
  return Number.isInteger(n) && n > 0 ? n : 1
})
const viewerItemId = computed(() => {
  const n = parseInt(route.query.media, 10)
  return Number.isInteger(n) && n > 0 ? n : null
})

// La carte est rechargée à chaque visite de l'écran (de nouvelles galeries ont pu être ajoutées).
const mapReady = loadMap()

// Tracé en quasi temps réel : rechargé toutes les minutes tant que l'onglet est visible (et dès qu'il le redevient).
const TRACK_REFRESH_MS = 60_000
loadTrack()
const trackTimer = setInterval(() => {
  if (document.visibilityState === 'visible') loadTrack()
}, TRACK_REFRESH_MS)
function onVisibilityChange() {
  if (document.visibilityState === 'visible') loadTrack()
}
document.addEventListener('visibilitychange', onVisibilityChange)
onBeforeUnmount(() => {
  clearInterval(trackTimer)
  document.removeEventListener('visibilitychange', onVisibilityChange)
})

// Légende : seulement les sources présentes dans le tracé.
const trackSources = computed(() => [
  ...new Set(journal.track.segments.map((segment) => segment.source).filter((source) => TRACK_SOURCES[source])),
])

// ---------------------------------------------------------------------------
// Liste : chargement de la bonne page, sélection, défilement
// ---------------------------------------------------------------------------

let run = 0
let first = true
let suppressScroll = false
const settled = ref(false) // false tant que la page demandée n'est pas chargée

async function sync(force = false) {
  const current = ++run
  const initial = first
  first = false
  settled.value = false

  let target = queryPage.value
  if (selectedId.value !== null) {
    await mapReady
    if (current !== run) return
    const gallery = journal.galleries.find((g) => g.id === selectedId.value)
    if (!gallery) {
      router.replace({ name: 'home' }) // galerie inconnue ou supprimée
      return
    }
    target = gallery.page
  }

  const pageChanged = initial || force || !journal.data || journal.data.page !== target
  if (pageChanged) {
    await loadPage(target)
    if (current !== run) return
    const total = journal.data?.totalPages ?? 0
    if (selectedId.value === null && total > 0 && target > total) {
      // Page demandée au-delà de la dernière : on revient sur la dernière.
      router.replace({ name: 'home', query: total > 1 ? { page: total } : {} })
      return
    }
  }

  await nextTick()
  if (current !== run) return
  if (selectedId.value !== null) {
    if (!suppressScroll) scrollToGallery(selectedId.value, !pageChanged)
  } else if (pageChanged) {
    listElement.value?.scrollTo({ top: 0 })
  }
  suppressScroll = false
  settled.value = true
}

// La visionneuse (?media=) ne doit pas relancer le chargement : on ne surveille que la page et la galerie.
watch(
  () => `${String(route.name)}|${route.params.id ?? ''}|${route.query.page ?? ''}`,
  () => sync(),
  { immediate: true },
)

function scrollToGallery(id, smooth) {
  const list = listElement.value
  const card = document.getElementById(`galerie-${id}`)
  if (!list || !card) return
  const top = card.getBoundingClientRect().top - list.getBoundingClientRect().top + list.scrollTop - 12
  list.scrollTo({ top, behavior: smooth ? 'smooth' : 'auto' })
}

// Sélection depuis la carte (scrollToCard = true) ou depuis un en-tête de galerie (false : elle est déjà à l'écran).
async function selectGallery(id, scrollToCard) {
  const gallery = journal.galleries.find((g) => g.id === id)
  if (!gallery) return
  // Carte agrandie sur mobile : on la réduit pour montrer la galerie choisie (et on attend que la mise en page suive).
  if (scrollToCard && mapExpanded.value) {
    mapExpanded.value = false
    await nextTick()
  }
  if (id === selectedId.value) {
    if (scrollToCard) scrollToGallery(id, true)
    return
  }
  suppressScroll = !scrollToCard
  const location = { name: 'gallery', params: { id } }
  // Changer de page = nouvelle entrée d'historique (le retour fonctionne) ; sinon on remplace.
  if (gallery.page === journal.data?.page) router.replace(location)
  else router.push(location)
}

// ---------------------------------------------------------------------------
// Carte agrandie (mobile) et titre de l'onglet
// ---------------------------------------------------------------------------

const mapExpanded = ref(false)

const BASE_TITLE = 'Journal de voyage'
const selectedGallery = computed(() => journal.galleries.find((g) => g.id === selectedId.value))
watch(
  selectedGallery,
  (gallery) => {
    document.title = gallery ? `${gallery.title} · ${gallery.placeName} — ${BASE_TITLE}` : BASE_TITLE
  },
  { immediate: true },
)
onBeforeUnmount(() => {
  document.title = BASE_TITLE
})

// ---------------------------------------------------------------------------
// Visionneuse
// ---------------------------------------------------------------------------

// Média demandé par l'adresse, retrouvé dans la page chargée, avec ses voisins (photos et vidéos de la galerie).
const viewer = computed(() => {
  const id = viewerItemId.value
  if (id === null || !journal.data) return null
  for (const gallery of journal.data.content) {
    const media = gallery.items.filter((item) => item.type !== 'TEXT')
    const index = media.findIndex((item) => item.id === id)
    if (index >= 0) return { gallery, media, index }
  }
  return null
})

let openedByUs = false // vrai si c'est un clic qui a ajouté l'entrée d'historique

function locationWith(mediaId) {
  const { media, ...query } = route.query
  return { name: route.name, params: route.params, query: mediaId ? { ...query, media: mediaId } : query }
}

function open(gallery, item) {
  openedByUs = true
  router.push(locationWith(item.id))
}

function closeViewer() {
  if (openedByUs) {
    openedByUs = false
    router.back() // revient exactement où l'on était
  } else {
    router.replace(locationWith(null)) // arrivée directe par un lien
  }
}

function navigateViewer(index) {
  router.replace(locationWith(viewer.value.media[index].id)) // pas d'entrée d'historique supplémentaire
}

watch([settled, viewerItemId, viewer], () => {
  if (viewerItemId.value === null) {
    openedByUs = false
  } else if (settled.value && !viewer.value) {
    router.replace(locationWith(null)) // média inconnu ou supprimé
  }
})
</script>

<template>
  <div class="home" :class="{ 'home--map-expanded': mapExpanded }">
    <section class="home__map" aria-label="Carte du voyage">
      <TravelMap
        :galleries="journal.galleries"
        :selected-id="selectedId"
        :track="journal.track"
        :galleries-loaded="journal.mapLoaded"
        @select="(id) => selectGallery(id, true)"
      />
      <ul v-if="trackSources.length > 0" class="map-legend" aria-label="Légende des trajets">
        <li v-for="source in trackSources" :key="source" :style="{ color: `var(${TRACK_SOURCES[source].cssVar})` }">
          <svg viewBox="0 0 28 6" aria-hidden="true">
            <line
              x1="3"
              y1="3"
              x2="25"
              y2="3"
              stroke="currentColor"
              :stroke-width="TRACK_SOURCES[source].weight"
              stroke-linecap="round"
              :stroke-dasharray="TRACK_SOURCES[source].dash ?? undefined"
            />
          </svg>
          <span>{{ TRACK_SOURCES[source].label }}</span>
        </li>
      </ul>
      <!-- Visible sur mobile seulement : la carte passe en plein écran, ou revient à sa taille normale -->
      <button
        class="map-toggle"
        type="button"
        :aria-expanded="mapExpanded"
        :aria-label="mapExpanded ? 'Réduire la carte' : 'Agrandir la carte'"
        @click="mapExpanded = !mapExpanded"
      >
        <svg v-if="!mapExpanded" viewBox="0 0 24 24" aria-hidden="true"><path d="M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5" /></svg>
        <svg v-else viewBox="0 0 24 24" aria-hidden="true"><path d="M9 4v5H4M15 4v5h5M9 20v-5H4M15 20v-5h5" /></svg>
      </button>
    </section>

    <main ref="listElement" class="home__list">
      <header class="home__header">
        <h1>Journal de voyage</h1>
      </header>

      <p v-if="journal.error" class="error" role="alert">
        Impossible de charger les galeries.
        <button class="btn btn--ghost btn--small" type="button" @click="sync(true)">Réessayer</button>
      </p>
      <p v-else-if="!journal.data" class="muted">Chargement…</p>
      <p v-else-if="journal.data.content.length === 0" class="muted">Aucune galerie pour le moment.</p>

      <template v-else>
        <div class="timeline">
          <GalleryCard
            v-for="gallery in journal.data.content"
            :key="gallery.id"
            :gallery="gallery"
            :selected="gallery.id === selectedId"
            @select="(id) => selectGallery(id, false)"
            @open="open"
          />
        </div>
        <Pagination :page="journal.data.page" :total-pages="journal.data.totalPages" />
      </template>

      <footer class="home__footer">
        <RouterLink to="/admin" class="muted">Administration</RouterLink>
      </footer>
    </main>

    <MediaViewer
      v-if="viewer"
      :items="viewer.media"
      :index="viewer.index"
      :title="viewer.gallery.title"
      @close="closeViewer"
      @navigate="navigateViewer"
    />
  </div>
</template>
