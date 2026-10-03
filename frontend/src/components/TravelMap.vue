<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { formatDate } from '../format'

const props = defineProps({
  galleries: { type: Array, default: () => [] },
  selectedId: { type: Number, default: null },
})
const emit = defineEmits(['select'])

const element = ref(null)

// Objets Leaflet gardés hors de la réactivité de Vue (inutile et coûteux de les proxifier).
let map = null
let layer = null
let observer = null
let fitted = false
let reducedMotion = false
const markers = new Map() // id de galerie -> { marker, gallery }

const PIN_PATH = 'M16 1C7.7 1 1 7.6 1 15.7 1 26.5 16 39 16 39s15-12.5 15-23.3C31 7.6 24.3 1 16 1z'

function pinIcon(selected) {
  const [width, height] = selected ? [40, 50] : [30, 38]
  return L.divIcon({
    className: selected ? 'pin pin--selected' : 'pin',
    html: `<svg viewBox="0 0 32 40" aria-hidden="true"><path d="${PIN_PATH}" /><circle cx="16" cy="15.5" r="5.5" /></svg>`,
    iconSize: [width, height],
    iconAnchor: [width / 2, height],
    tooltipAnchor: [0, -height + 4],
  })
}

// Contenu construit à la demande (à l'ouverture), avec textContent : le titre est saisi par un admin,
// on ne l'insère jamais comme du HTML. Les miniatures ne sont donc chargées que pour les infobulles affichées.
function tooltipContent(gallery) {
  const body = document.createElement('div')
  body.className = 'pin-tip__body'
  if (gallery.coverUrl) {
    const image = document.createElement('img')
    image.src = gallery.coverUrl
    image.alt = ''
    image.width = 56
    image.height = 56
    body.append(image)
  }
  const text = document.createElement('div')
  const title = document.createElement('strong')
  title.textContent = gallery.title
  const meta = document.createElement('span')
  meta.textContent = `${formatDate(gallery.galleryDate)} · ${gallery.placeName}`
  text.append(title, meta)
  body.append(text)
  return body
}

function bindTooltip(entry, permanent) {
  entry.marker.unbindTooltip()
  entry.marker.bindTooltip(() => tooltipContent(entry.gallery), {
    direction: 'top',
    opacity: 1,
    className: 'pin-tip',
    permanent,
  })
}

function describe(entry) {
  const markerElement = entry.marker.getElement()
  if (markerElement) {
    markerElement.setAttribute('role', 'button')
    markerElement.setAttribute('aria-label', `${entry.gallery.title}, ${entry.gallery.placeName}`)
  }
}

function render() {
  if (!map) return
  layer.clearLayers()
  markers.clear()
  for (const gallery of props.galleries) {
    const marker = L.marker([gallery.latitude, gallery.longitude], { icon: pinIcon(false), riseOnHover: true })
    marker.on('click', () => emit('select', gallery.id))
    marker.addTo(layer)
    const entry = { marker, gallery }
    bindTooltip(entry, false)
    describe(entry)
    markers.set(gallery.id, entry)
  }
  if (!fitted && props.galleries.length > 0) {
    fitted = true
    map.fitBounds(L.latLngBounds(props.galleries.map((g) => [g.latitude, g.longitude])), {
      padding: [40, 40],
      maxZoom: 10,
      animate: false,
    })
  }
  applySelection(props.selectedId, null)
}

function style(entry, selected) {
  entry.marker.setIcon(pinIcon(selected))
  entry.marker.setZIndexOffset(selected ? 1000 : 0)
  bindTooltip(entry, selected)
  describe(entry)
  if (selected) entry.marker.openTooltip()
}

function applySelection(id, previousId) {
  if (!map) return
  const previous = previousId != null ? markers.get(previousId) : null
  if (previous) style(previous, false)
  const current = id != null ? markers.get(id) : null
  if (current) {
    style(current, true)
    map.setView(current.marker.getLatLng(), Math.max(map.getZoom(), 6), { animate: !reducedMotion })
  }
}

onMounted(() => {
  // Peu d'animations : pas de fondu des tuiles ni d'animation des marqueurs, et aucune animation
  // de zoom si le visiteur a demandé de réduire les animations.
  reducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches ?? false
  map = L.map(element.value, {
    worldCopyJump: true,
    fadeAnimation: false,
    markerZoomAnimation: false,
    zoomAnimation: !reducedMotion,
  })
  map.attributionControl.setPrefix(false)
  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution:
      '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a>',
  }).addTo(map)
  map.setView([20, 0], 2)
  layer = L.layerGroup().addTo(map)

  // La carte doit être recalculée quand son conteneur change de taille (fenêtre, rotation du téléphone...).
  observer = new ResizeObserver(() => map.invalidateSize())
  observer.observe(element.value)

  render()
})

onBeforeUnmount(() => {
  observer?.disconnect()
  map?.remove()
  map = null
  markers.clear()
})

watch(() => props.galleries, render)
watch(() => props.selectedId, (id, previous) => applySelection(id, previous))
</script>

<template>
  <div ref="element" class="travel-map"></div>
</template>
