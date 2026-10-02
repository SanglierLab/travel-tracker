<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import GalleryCard from '../components/GalleryCard.vue'
import Pagination from '../components/Pagination.vue'
import { journal, loadPage } from '../journal'

const route = useRoute()
const router = useRouter()
const listElement = ref(null)

const page = computed(() => {
  const n = parseInt(route.query.page, 10)
  return Number.isInteger(n) && n > 0 ? n : 1
})

watch(
  page,
  async (current) => {
    await loadPage(current)
    const total = journal.data?.totalPages ?? 0
    if (total > 0 && current > total) {
      // Page demandée au-delà de la dernière : on revient sur la dernière.
      router.replace({ query: total > 1 ? { page: total } : {} })
      return
    }
    await nextTick()
    listElement.value?.scrollTo({ top: 0 })
  },
  { immediate: true },
)

function select(id) {
  journal.selectedId = id
}

// Provisoire (étape 2a) : la visionneuse plein écran arrive à l'étape 2c.
function open(gallery, item) {
  window.open(item.displayUrl || item.originalUrl, '_blank', 'noopener')
}
</script>

<template>
  <div class="home">
    <section class="home__map" aria-label="Carte du voyage">
      <p class="home__map-placeholder">La carte arrive à l'étape suivante.</p>
    </section>

    <main ref="listElement" class="home__list">
      <header class="home__header">
        <h1>Journal de voyage</h1>
      </header>

      <p v-if="journal.error" class="error" role="alert">
        Impossible de charger les galeries.
        <button class="btn btn--ghost btn--small" type="button" @click="loadPage(page)">Réessayer</button>
      </p>
      <p v-else-if="!journal.data" class="muted">Chargement…</p>
      <p v-else-if="journal.data.content.length === 0" class="muted">Aucune galerie pour le moment.</p>

      <template v-else>
        <div class="timeline">
          <GalleryCard
            v-for="gallery in journal.data.content"
            :key="gallery.id"
            :gallery="gallery"
            :selected="gallery.id === journal.selectedId"
            @select="select"
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
