<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { L, createMap, pinIcon, prefersReducedMotion } from '../leafletSupport'
import { formatDate, formatDateTime, timeAgo } from '../format'
import { TRACK_SOURCES, trackColor } from '../trackStyle'

const props = defineProps({
  galleries: { type: Array, default: () => [] },
  selectedId: { type: Number, default: null },
  // Tracé de la route (GET /api/public/track) :
  //   { segments: [{ source, tripId, start, end, points: [[lat, lon], ...] }], last: { source, latitude, longitude, recordedAt } | null }
  track: { type: Object, default: () => ({ segments: [], last: null }) },
  // La carte ne se cadre qu'une fois la liste des galeries connue (même vide).
  galleriesLoaded: { type: Boolean, default: true },
})
const emit = defineEmits(['select'])

const element = ref(null)

// Objets Leaflet gardés hors de la réactivité de Vue (inutile et coûteux de les proxifier).
let map = null
let layer = null
let trackLayer = null
let observer = null
let fitted = false
const markers = new Map() // id de galerie -> { marker, gallery }

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
  fitInitial()
  applySelection(props.selectedId, null)
}

// Cadrage initial : tout ce qui est connu (galeries et tracé) tient à l'écran. Une seule fois.
function fitInitial() {
  if (fitted || !map || !props.galleriesLoaded) return
  const points = props.galleries.map((g) => [g.latitude, g.longitude])
  for (const segment of props.track.segments) points.push(...segment.points)
  if (points.length === 0) return
  fitted = true
  map.fitBounds(L.latLngBounds(points), { padding: [40, 40], maxZoom: 10, animate: false })
}

// Tracé : un style par source (voir trackStyle.js). Redessiné à chaque rafraîchissement (quelques dizaines de lignes).
function drawTrack() {
  if (!map) return
  trackLayer.clearLayers()
  for (const segment of props.track.segments) {
    const source = TRACK_SOURCES[segment.source]
    if (!source || segment.points.length === 0) continue
    const color = trackColor(segment.source)
    if (segment.points.length === 1) {
      // Point isolé (ex. premier point après une longue pause) : une simple pastille.
      L.circleMarker(segment.points[0], { radius: 3, weight: 0, fillColor: color, fillOpacity: 0.9, interactive: false }).addTo(trackLayer)
      continue
    }
    L.polyline(segment.points, {
      color,
      weight: source.weight,
      opacity: 0.85,
      dashArray: source.dash ?? undefined,
      lineCap: 'round',
      lineJoin: 'round',
      smoothFactor: 1.5,
    })
      .bindTooltip(() => `${source.label} · ${formatDateTime(segment.start)} → ${formatDateTime(segment.end)}`, {
        sticky: true,
        className: 'pin-tip',
      })
      .addTo(trackLayer)
  }

  // Dernière position connue : la « tête » du petit poucet.
  const last = props.track.last
  if (last && TRACK_SOURCES[last.source]) {
    L.circleMarker([last.latitude, last.longitude], {
      radius: 8,
      color: '#fff',
      weight: 3,
      fillColor: trackColor(last.source),
      fillOpacity: 1,
    })
      .bindTooltip(() => `Dernière position connue · ${timeAgo(last.recordedAt)}`, { direction: 'top', className: 'pin-tip' })
      .addTo(trackLayer)
  }
  fitInitial()
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
    map.setView(current.marker.getLatLng(), Math.max(map.getZoom(), 6), { animate: !prefersReducedMotion() })
  }
}

onMounted(() => {
  map = createMap(element.value, { worldCopyJump: true })
  map.setView([20, 0], 2)
  trackLayer = L.layerGroup().addTo(map)
  layer = L.layerGroup().addTo(map)

  // La carte doit être recalculée quand son conteneur change de taille (fenêtre, rotation du téléphone...).
  observer = new ResizeObserver(() => map.invalidateSize())
  observer.observe(element.value)

  render()
  drawTrack()
})

onBeforeUnmount(() => {
  observer?.disconnect()
  map?.remove()
  map = null
  markers.clear()
})

watch(() => props.galleries, render)
watch(() => props.track, drawTrack)
watch(() => props.galleriesLoaded, fitInitial)
watch(() => props.selectedId, (id, previous) => applySelection(id, previous))
</script>

<template>
  <div ref="element" class="travel-map map-washi"></div>
</template>
