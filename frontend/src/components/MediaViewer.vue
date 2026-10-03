<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { formatSize } from '../format'

const props = defineProps({
  items: { type: Array, required: true }, // photos et vidéos de la galerie, dans l'ordre
  index: { type: Number, required: true },
  title: { type: String, default: '' },
})
const emit = defineEmits(['close', 'navigate'])

const root = ref(null)
const closeButton = ref(null)
const videoFailed = ref(false)

const item = computed(() => props.items[props.index])
const size = computed(() => formatSize(item.value.sizeBytes))

function go(delta) {
  const target = props.index + delta
  if (target >= 0 && target < props.items.length) emit('navigate', target)
}

// --- Clavier : Échap, flèches, et Tab qui reste dans la fenêtre ---
function onKey(event) {
  if (event.key === 'Escape') {
    event.preventDefault()
    emit('close')
  } else if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
    // Dans un lecteur vidéo, les flèches servent à avancer/reculer dans la vidéo.
    if (document.activeElement?.tagName === 'VIDEO') return
    go(event.key === 'ArrowLeft' ? -1 : 1)
  } else if (event.key === 'Tab') {
    const focusable = [...root.value.querySelectorAll('a[href], button:not([disabled])')]
    if (focusable.length === 0) return
    const first = focusable[0]
    const last = focusable[focusable.length - 1]
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault()
      last.focus()
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault()
      first.focus()
    }
  }
}

// --- Balayage au doigt (photos seulement ; ignoré quand on a zoomé avec deux doigts) ---
let touchStart = null
function onTouchStart(event) {
  touchStart = event.touches.length === 1 ? { x: event.touches[0].clientX, y: event.touches[0].clientY } : null
}
function onTouchEnd(event) {
  const start = touchStart
  touchStart = null
  if (!start || item.value.type === 'VIDEO' || (window.visualViewport?.scale ?? 1) > 1.05) return
  const touch = event.changedTouches[0]
  const dx = touch.clientX - start.x
  const dy = touch.clientY - start.y
  if (Math.abs(dx) > 50 && Math.abs(dx) > Math.abs(dy) * 1.5) go(dx < 0 ? 1 : -1)
}

// Précharge les photos voisines : le passage à la suivante est immédiat.
function preloadNeighbours() {
  for (const neighbour of [props.items[props.index - 1], props.items[props.index + 1]]) {
    if (neighbour?.type === 'PHOTO') new Image().src = neighbour.displayUrl
  }
}

watch(
  () => props.index,
  () => {
    videoFailed.value = false
    preloadNeighbours()
  },
  { immediate: true },
)

let previouslyFocused = null
onMounted(async () => {
  previouslyFocused = document.activeElement
  window.addEventListener('keydown', onKey)
  await nextTick()
  closeButton.value?.focus()
})
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKey)
  previouslyFocused?.focus?.()
})
</script>

<template>
  <div
    ref="root"
    class="viewer"
    role="dialog"
    aria-modal="true"
    :aria-label="`${title} : ${index + 1} sur ${items.length}`"
  >
    <div class="viewer__bar">
      <button ref="closeButton" class="viewer__btn" type="button" aria-label="Fermer" @click="$emit('close')">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18" /></svg>
      </button>
      <p class="viewer__title">{{ title }}</p>
      <span class="viewer__count">{{ index + 1 }} / {{ items.length }}</span>
      <a
        class="viewer__btn"
        :href="item.originalUrl"
        :download="item.originalFilename || ''"
        aria-label="Télécharger l'original"
      >
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 4v11M7 11l5 5 5-5M5 20h14" /></svg>
        <span class="viewer__label">Original<template v-if="size"> · {{ size }}</template></span>
      </a>
    </div>

    <div class="viewer__stage" @touchstart.passive="onTouchStart" @touchend="onTouchEnd">
      <template v-if="item.type === 'PHOTO'">
        <!-- La miniature (déjà en cache) s'affiche tout de suite, la grande image la recouvre en arrivant -->
        <img v-if="item.thumbUrl" class="viewer__img" :src="item.thumbUrl" alt="" aria-hidden="true" />
        <img
          :key="item.id"
          class="viewer__img"
          :src="item.displayUrl"
          :alt="`${title} (${index + 1}/${items.length})`"
        />
      </template>

      <template v-else>
        <div v-if="videoFailed" class="viewer__error" role="alert">
          <p>Cette vidéo ne peut pas être lue par ce navigateur.</p>
          <p>Vous pouvez la télécharger avec le bouton « Original ».</p>
        </div>
        <video
          v-else
          :key="item.id"
          class="viewer__video"
          :src="item.originalUrl"
          :poster="item.thumbUrl || undefined"
          controls
          autoplay
          playsinline
          preload="metadata"
          @error="videoFailed = true"
        ></video>
      </template>

      <button
        v-if="index > 0"
        class="viewer__btn viewer__nav viewer__nav--prev"
        type="button"
        aria-label="Précédent"
        @click="go(-1)"
      >
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M15 5l-7 7 7 7" /></svg>
      </button>
      <button
        v-if="index < items.length - 1"
        class="viewer__btn viewer__nav viewer__nav--next"
        type="button"
        aria-label="Suivant"
        @click="go(1)"
      >
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9 5l7 7-7 7" /></svg>
      </button>
    </div>
  </div>
</template>
