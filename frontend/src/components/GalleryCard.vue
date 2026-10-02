<script setup>
import { computed } from 'vue'
import { formatDate } from '../format'
import MediaTile from './MediaTile.vue'

const props = defineProps({
  gallery: { type: Object, required: true },
  selected: { type: Boolean, default: false },
})
defineEmits(['select', 'open'])

// Les médias qui se suivent forment une grille ; un bloc de texte l'interrompt.
const blocks = computed(() => {
  const result = []
  for (const item of props.gallery.items) {
    if (item.type === 'TEXT') {
      result.push({ kind: 'text', key: `t${item.id}`, item })
    } else {
      const last = result[result.length - 1]
      if (last && last.kind === 'grid') last.items.push(item)
      else result.push({ kind: 'grid', key: `g${item.id}`, items: [item] })
    }
  }
  return result
})

function label(item) {
  return `${item.type === 'VIDEO' ? 'Vidéo' : 'Photo'} : ${props.gallery.title}`
}
</script>

<template>
  <article
    :id="`galerie-${gallery.id}`"
    class="gallery"
    :class="{ 'gallery--selected': selected }"
  >
    <span class="gallery__node" aria-hidden="true"></span>

    <header class="gallery__head" @click="$emit('select', gallery.id)">
      <p class="gallery__meta">
        <time :datetime="gallery.galleryDate">{{ formatDate(gallery.galleryDate) }}</time>
        · {{ gallery.placeName }}
      </p>
      <h2 class="gallery__title">{{ gallery.title }}</h2>
    </header>

    <p v-if="blocks.length === 0" class="gallery__empty muted">Cette galerie est encore vide.</p>

    <template v-for="block in blocks" :key="block.key">
      <!-- html : produit et assaini côté serveur (HTML brut échappé, URL dangereuses retirées) -->
      <div v-if="block.kind === 'text'" class="prose" v-html="block.item.html"></div>
      <div v-else class="media-grid">
        <MediaTile
          v-for="item in block.items"
          :key="item.id"
          :item="item"
          :label="label(item)"
          @open="$emit('open', gallery, item)"
        />
      </div>
    </template>
  </article>
</template>
