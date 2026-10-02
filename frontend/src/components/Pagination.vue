<script setup>
import { computed } from 'vue'

const props = defineProps({
  page: { type: Number, required: true },
  totalPages: { type: Number, required: true },
})

// Page 1 = les galeries les plus récentes. La page 1 n'a pas de paramètre dans l'adresse.
function target(n) {
  return { name: 'home', query: n === 1 ? {} : { page: n } }
}
const newer = computed(() => (props.page > 1 ? target(props.page - 1) : null))
const older = computed(() => (props.page < props.totalPages ? target(props.page + 1) : null))
</script>

<template>
  <nav v-if="totalPages > 1" class="pager" aria-label="Pagination">
    <RouterLink v-if="newer" class="btn btn--ghost btn--small" :to="newer">‹ Plus récentes</RouterLink>
    <span v-else class="btn btn--ghost btn--small" aria-disabled="true">‹ Plus récentes</span>

    <span class="pager__info">Page {{ page }} sur {{ totalPages }}</span>

    <RouterLink v-if="older" class="btn btn--ghost btn--small" :to="older">Plus anciennes ›</RouterLink>
    <span v-else class="btn btn--ghost btn--small" aria-disabled="true">Plus anciennes ›</span>
  </nav>
</template>
