<script setup>
import { computed, defineAsyncComponent, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import GalleryCard from '../components/GalleryCard.vue'
import StageInterstitial from '../components/StageInterstitial.vue'
import MediaViewer from '../components/MediaViewer.vue'
import Pagination from '../components/Pagination.vue'
import { journal, loadItinerary, loadMap, loadPage, loadTrack } from '../journal'
import { STAGE_KINDS, KIND_ORDER, isTrip, stageKindClass } from '../stageKinds'
import { isFuture, isImminent } from '../stageGeo'
import StageIcon from '../components/StageIcon.vue'
import { TRACK_SOURCES, isLate } from '../trackStyle'

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
//   /etape/3          -> étape 3 sélectionnée sur la carte (la liste reste où elle est)
const selectedStageId = computed(() => (route.name === 'stage' ? Number(route.params.id) : null))
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
const itineraryReady = loadItinerary()

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

const lastLate = computed(() => isLate(journal.track))

// Le fil mélange albums et étapes commencées ; les étapes à venir sont à part, dans un bloc repliable.
const entries = computed(() => journal.data?.content ?? [])
// Du plus lointain au plus proche, comme le fil (à date égale, la plus avancée dans l'itinéraire d'abord).
const futureStages = computed(() =>
  journal.itinerary
    .filter((stage) => isFuture(stage, journal.today))
    .reverse()
    .sort((a, b) => (a.startDate < b.startDate ? 1 : a.startDate > b.startDate ? -1 : 0)), // tri stable
)
// Les étapes imminentes sont en tête du fil (sur la ligne du temps) ; les autres sont dans le bloc repliable.
const imminentStages = computed(() => futureStages.value.filter((stage) => isImminent(stage, journal.today, journal.upcomingDays)))
const farStages = computed(() => futureStages.value.filter((stage) => !imminentStages.value.includes(stage)))
const upcomingOpen = ref(false)

// Légende : types d'étape présents, et mention « à venir » s'il y a des étapes futures.
const stageKindsShown = computed(() => KIND_ORDER.filter((kind) => journal.itinerary.some((stage) => stage.kind === kind)))
const hasFutureStage = computed(() => futureStages.value.length > 0)
const hasLegend = computed(() => trackSources.value.length > 0 || stageKindsShown.value.length > 0 || journal.galleries.length > 0)

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
  } else if (selectedStageId.value !== null) {
    await itineraryReady
    if (current !== run) return
    const stage = journal.itinerary.find((s) => s.id === selectedStageId.value)
    if (!stage) {
      router.replace({ name: 'home' }) // étape inconnue ou supprimée
      return
    }
    target = stage.page ?? 1 // une étape à venir n'est pas dans le fil : le bloc « suite du voyage » est en haut de la première page
  }

  const pageChanged = initial || force || !journal.data || journal.data.page !== target
  if (pageChanged) {
    await loadPage(target)
    if (current !== run) return
    const total = journal.data?.totalPages ?? 0
    if (selectedId.value === null && selectedStageId.value === null && total > 0 && target > total) {
      // Page demandée au-delà de la dernière : on revient sur la dernière.
      router.replace({ name: 'home', query: total > 1 ? { page: total } : {} })
      return
    }
  }

  await nextTick()
  if (current !== run) return
  if (selectedId.value !== null) {
    if (!suppressScroll) scrollToElement(`galerie-${selectedId.value}`, !pageChanged)
  } else if (selectedStageId.value !== null) {
    if (farStages.value.some((stage) => stage.id === selectedStageId.value)) upcomingOpen.value = true
    await nextTick()
    if (current !== run) return
    if (!suppressScroll) scrollToElement(`etape-${selectedStageId.value}`, !pageChanged)
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

function scrollToElement(elementId, smooth) {
  const list = listElement.value
  const card = document.getElementById(elementId)
  if (!list || !card) return
  const top = card.getBoundingClientRect().top - list.getBoundingClientRect().top + list.scrollTop - 12
  list.scrollTo({ top, behavior: smooth ? 'smooth' : 'auto' })
}

// Sélection d'une étape depuis la carte (scrollToCard = true) ou depuis le fil (false : elle est déjà à l'écran).
async function selectStage(id, scrollToCard) {
  const stage = journal.itinerary.find((s) => s.id === id)
  if (!stage) return
  if (scrollToCard && mapExpanded.value) {
    mapExpanded.value = false
    await nextTick()
  }
  if (id === selectedStageId.value) {
    if (scrollToCard) scrollToElement(`etape-${id}`, true)
    return
  }
  suppressScroll = !scrollToCard
  const location = { name: 'stage', params: { id } }
  // Même logique que pour les galeries : changer de page ajoute une entrée d'historique, sinon on remplace.
  if (stage.page === journal.data?.page || (stage.page == null && journal.data?.page === 1)) router.replace(location)
  else router.push(location)
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
    if (scrollToCard) scrollToElement(`galerie-${id}`, true)
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

// ---------------------------------------------------------------------------
// Séparateur carte / liste (grand écran) : on le déplace à la souris, au doigt ou au clavier ; la largeur de la liste est
// retenue (en part de la fenêtre) dans ce navigateur. Double clic ou Échap : retour à la largeur par défaut.
// ---------------------------------------------------------------------------

const LIST_SHARE_KEY = 'journal.listShare'
const LIST_SHARE_MIN = 0.25
const LIST_SHARE_MAX = 0.75
const LIST_SHARE_STEP = 0.02

// Bornes : 25 à 75 % de la fenêtre, mais jamais moins de 22 rem pour la liste ni de 20 rem pour la carte.
function clampShare(value) {
  const rem = parseFloat(getComputedStyle(document.documentElement).fontSize) || 16
  const width = window.innerWidth || 1
  const min = Math.max(LIST_SHARE_MIN, (22 * rem) / width)
  const max = Math.max(min, Math.min(LIST_SHARE_MAX, 1 - (20 * rem) / width))
  return Math.min(max, Math.max(min, value))
}

function readShare() {
  try {
    const value = Number(localStorage.getItem(LIST_SHARE_KEY))
    return Number.isFinite(value) && value > 0 ? clampShare(value) : null
  } catch {
    return null
  }
}

function saveShare(value) {
  try {
    if (value === null) localStorage.removeItem(LIST_SHARE_KEY)
    else localStorage.setItem(LIST_SHARE_KEY, String(value))
  } catch {
    // stockage indisponible : le réglage vaut pour cette visite seulement
  }
}

const listShare = ref(readShare()) // part de la largeur de la fenêtre prise par la liste ; null = largeur par défaut
const resizing = ref(false)
const splitter = ref(null)
const homeStyle = computed(() => (listShare.value === null ? undefined : { '--list-width': `${(listShare.value * 100).toFixed(2)}vw` }))

function currentShare() {
  const list = listElement.value
  return listShare.value ?? (list ? list.getBoundingClientRect().width / window.innerWidth : 0.4)
}

function startResize(event) {
  if (event.button !== undefined && event.button !== 0) return
  resizing.value = true
  splitter.value?.setPointerCapture?.(event.pointerId)
}

function moveResize(event) {
  if (!resizing.value) return
  listShare.value = clampShare(1 - event.clientX / window.innerWidth)
}

function endResize() {
  if (!resizing.value) return
  resizing.value = false
  saveShare(listShare.value)
}

function resetShare() {
  listShare.value = null
  saveShare(null)
}

function keyResize(event) {
  const share = currentShare()
  if (event.key === 'ArrowLeft') listShare.value = clampShare(share + LIST_SHARE_STEP)
  else if (event.key === 'ArrowRight') listShare.value = clampShare(share - LIST_SHARE_STEP)
  else if (event.key === 'Escape' || event.key === 'Home') resetShare()
  else return
  event.preventDefault()
  if (listShare.value !== null) saveShare(listShare.value)
}

const BASE_TITLE = 'Journal de voyage'
const selectedGallery = computed(() => journal.galleries.find((g) => g.id === selectedId.value))
const selectedStage = computed(() => journal.itinerary.find((stage) => stage.id === selectedStageId.value))
watch(
  [selectedGallery, selectedStage],
  ([gallery, stage]) => {
    if (gallery) document.title = `${gallery.title} · ${gallery.placeName} — ${BASE_TITLE}`
    else if (stage) document.title = `${stage.name} — ${BASE_TITLE}`
    else document.title = BASE_TITLE
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
  for (const { gallery } of entries.value) {
    if (!gallery) continue
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
  <div class="home" :class="{ 'home--map-expanded': mapExpanded, 'home--resizing': resizing }" :style="homeStyle">
    <section class="home__map" aria-label="Carte du voyage">
      <TravelMap
        :galleries="journal.galleries"
        :selected-id="selectedId"
        :track="journal.track"
        :stages="journal.itinerary"
        :selected-stage-id="selectedStageId"
        @select-stage="(id) => selectStage(id, true)"
        :today="journal.today"
        :galleries-loaded="journal.mapLoaded && journal.itineraryLoaded"
        @select="(id) => selectGallery(id, true)"
      />
      <ul v-if="hasLegend" class="map-legend" aria-label="Légende de la carte">
        <li v-if="journal.galleries.length > 0">
          <svg class="map-legend__camera" viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7h3l1.5-2h7L17 7h3v11H4zM12 9.5a3.2 3.2 0 100 6.4 3.2 3.2 0 000-6.4z" /></svg>
          <span>Album photo</span>
        </li>
        <li v-for="kind in stageKindsShown" :key="kind" :class="stageKindClass(kind)" :style="{ color: 'var(--stage-color)' }">
          <StageIcon :kind="kind" class="map-legend__badge" />
          <svg v-if="isTrip(kind)" viewBox="0 0 28 6" aria-hidden="true">
            <line x1="3" y1="3" x2="25" y2="3" stroke="currentColor" :stroke-width="STAGE_KINDS[kind].weight - 1" stroke-linecap="round" opacity="0.7" :stroke-dasharray="STAGE_KINDS[kind].dash ?? undefined" />
          </svg>
          <span>{{ STAGE_KINDS[kind].label }}</span>
        </li>
        <li v-if="hasFutureStage">
          <StageIcon :kind="stageKindsShown[0]" class="map-legend__badge map-legend__badge--future" />
          <span>Étape à venir</span>
        </li>
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
        <li v-if="lastLate">
          <span class="map-legend__late" aria-hidden="true"></span>
          <span>Position en retard</span>
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

    <!-- Grand écran seulement (masqué en CSS en dessous) : séparateur déplaçable entre la carte et la liste -->
    <div
      ref="splitter"
      class="home__splitter"
      :class="{ 'home__splitter--dragging': resizing }"
      role="separator"
      aria-orientation="vertical"
      aria-label="Largeur de la liste (flèches gauche et droite pour la modifier, Échap pour la rétablir)"
      tabindex="0"
      @pointerdown.prevent="startResize"
      @pointermove="moveResize"
      @pointerup="endResize"
      @pointercancel="endResize"
      @dblclick="resetShare"
      @keydown="keyResize"
    ></div>

    <main ref="listElement" class="home__list">
      <header class="home__header">
        <h1>Journal de voyage</h1>
      </header>

      <p v-if="journal.error" class="error" role="alert">
        Impossible de charger les galeries.
        <button class="btn btn--ghost btn--small" type="button" @click="sync(true)">Réessayer</button>
      </p>
      <p v-else-if="!journal.data" class="muted">Chargement…</p>
      <p v-else-if="entries.length === 0 && futureStages.length === 0" class="muted">Rien à voir pour le moment.</p>

      <template v-else>
        <section v-if="farStages.length > 0 && journal.data.page === 1" class="upcoming">
          <button
            class="upcoming__toggle"
            type="button"
            :aria-expanded="upcomingOpen"
            @click="upcomingOpen = !upcomingOpen"
          >
            <svg class="upcoming__chevron" viewBox="0 0 24 24" aria-hidden="true"><path d="M9 6l6 6-6 6" /></svg>
            <span>{{ upcomingOpen ? 'Masquer la suite du voyage' : 'Voir la suite du voyage' }}</span>
            <span class="upcoming__count">{{ farStages.length }} {{ farStages.length > 1 ? 'étapes' : 'étape' }}</span>
          </button>
        </section>

        <div class="timeline">
          <!-- Étapes à venir, de la plus lointaine à la plus proche : d'abord celles du bloc déplié, puis les imminentes (toujours visibles) -->
          <template v-if="journal.data.page === 1">
            <template v-if="upcomingOpen">
              <StageInterstitial
                v-for="stage in farStages"
                :key="`far-${stage.id}`"
                :stage="stage"
                :selected="stage.id === selectedStageId"
                future
                @select="(id) => selectStage(id, false)"
              />
            </template>
            <StageInterstitial
              v-for="stage in imminentStages"
              :key="`imminent-${stage.id}`"
              :stage="stage"
              :selected="stage.id === selectedStageId"
              future
              @select="(id) => selectStage(id, false)"
            />
          </template>
          <template v-for="entry in entries" :key="entry.type + (entry.gallery?.id ?? entry.stage?.id)">
            <GalleryCard
              v-if="entry.type === 'GALLERY'"
              :gallery="entry.gallery"
              :selected="entry.gallery.id === selectedId"
              @select="(id) => selectGallery(id, false)"
              @open="open"
            />
            <StageInterstitial
              v-else
              :stage="entry.stage"
              :selected="entry.stage.id === selectedStageId"
              @select="(id) => selectStage(id, false)"
            />
          </template>
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
