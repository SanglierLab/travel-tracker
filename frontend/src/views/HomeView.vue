<script setup>
import { computed, defineAsyncComponent, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import GalleryCard from '../components/GalleryCard.vue'
import Pagination from '../components/Pagination.vue'
import { journal, loadMap, loadPage } from '../journal'

// Leaflet est chargé à part : la liste s'affiche sans attendre la bibliothèque de cartographie.
const TravelMap = defineAsyncComponent(() => import('../components/TravelMap.vue'))

const route = useRoute()
const router = useRouter()
const listElement = ref(null)

// L'adresse est l'unique source de vérité :
//   /?page=2        -> page 2, aucune galerie sélectionnée
//   /galerie/12     -> galerie 12 sélectionnée, sur la page où elle se trouve
const selectedId = computed(() => (route.name === 'gallery' ? Number(route.params.id) : null))
const queryPage = computed(() => {
  const n = parseInt(route.query.page, 10)
  return Number.isInteger(n) && n > 0 ? n : 1
})

// La carte est rechargée à chaque visite de l'écran (de nouvelles galeries ont pu être ajoutées).
const mapReady = loadMap()

let run = 0
let first = true
let suppressScroll = false

async function sync(force = false) {
  const current = ++run
  const initial = first
  first = false

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
}

watch(() => route.fullPath, () => sync(), { immediate: true })

function scrollToGallery(id, smooth) {
  const list = listElement.value
  const card = document.getElementById(`galerie-${id}`)
  if (!list || !card) return
  const top = card.getBoundingClientRect().top - list.getBoundingClientRect().top + list.scrollTop - 12
  list.scrollTo({ top, behavior: smooth ? 'smooth' : 'auto' })
}

// Sélection depuis la carte (scrollToCard = true) ou depuis un en-tête de galerie (false : elle est déjà à l'écran).
function selectGallery(id, scrollToCard) {
  const gallery = journal.galleries.find((g) => g.id === id)
  if (!gallery) return
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

// Provisoire (étape 2a) : la visionneuse plein écran arrive à l'étape 2c.
function open(gallery, item) {
  window.open(item.displayUrl || item.originalUrl, '_blank', 'noopener')
}
</script>

<template>
  <div class="home">
    <section class="home__map" aria-label="Carte du voyage">
      <TravelMap :galleries="journal.galleries" :selected-id="selectedId" @select="(id) => selectGallery(id, true)" />
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
  </div>
</template>
