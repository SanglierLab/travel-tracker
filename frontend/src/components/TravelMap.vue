<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { L, arrowIcon, createMap, liveIcon, pinIcon, prefersReducedMotion, stageIcon } from '../leafletSupport'
import { STAGE_KINDS, stageColor } from '../stageKinds'
import { isFuture, itineraryLinks, pointAlong, stageAnchor, stageLine, todayIso } from '../stageGeo'
import { formatDate, formatDateRange, formatDateTime, timeAgo } from '../format'
import { TRACK_SOURCES, isLate, itineraryColor, lastHeading, trackColor } from '../trackStyle'
import { openingFrame } from '../openingFrame'

const props = defineProps({
  galleries: { type: Array, default: () => [] },
  selectedId: { type: Number, default: null },
  // Tracé de la route (GET /api/public/track) :
  //   { segments: [{ source, tripId, start, end, points: [[lat, lon], ...] }], last: { source, latitude, longitude, recordedAt } | null }
  track: { type: Object, default: () => ({ segments: [], last: null }) },
  // Étapes du voyage (GET /api/public/itinerary), toutes affichées ; celles à venir sont estompées.
  stages: { type: Array, default: () => [] },
  // Date du jour (AAAA-MM-JJ) : une étape est à venir tant que son début la dépasse.
  today: { type: String, default: () => todayIso() },
  // Étape sélectionnée (adresse /etape/:id).
  selectedStageId: { type: Number, default: null },
  // La carte ne se cadre qu'une fois la liste des galeries connue (même vide).
  galleriesLoaded: { type: Boolean, default: true },
})
const emit = defineEmits(['select', 'select-stage'])

const element = ref(null)

// Objets Leaflet gardés hors de la réactivité de Vue (inutile et coûteux de les proxifier).
let map = null
let layer = null
let trackLayer = null
let stageLayer = null
let routeLayer = null
const stageEntries = new Map() // id d'étape -> { stage, marker, line, arrow, future }
let arrows = [] // pointes de flèche à réorienter quand le zoom change : { marker, from, to, small, future }
let observer = null
let fitted = false
const markers = new Map() // id de galerie -> { marker, gallery }

// Bouton « Fermer » d'une infobulle fixe (celle de l'élément sélectionné, qui ne se ferme pas toute seule).
// Fermer l'infobulle ne désélectionne pas : un nouveau clic sur le marqueur la rouvre.
function closeButton(onClose) {
  const button = document.createElement('button')
  button.type = 'button'
  button.className = 'pin-tip__close'
  button.textContent = '×'
  button.setAttribute('aria-label', 'Fermer')
  L.DomEvent.on(button, 'click', (event) => {
    L.DomEvent.stop(event)
    onClose()
  })
  return button
}

// Contenu construit à la demande (à l'ouverture), avec textContent : le titre est saisi par un admin,
// on ne l'insère jamais comme du HTML. Les miniatures ne sont donc chargées que pour les infobulles affichées.
function tooltipContent(gallery, onClose = null) {
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
  // Même présentation que l'infobulle d'une étape : le titre, puis une ligne par information (date, lieu).
  const text = document.createElement('div')
  text.className = 'stage-tip'
  const title = document.createElement('strong')
  title.textContent = gallery.title
  const date = document.createElement('span')
  date.textContent = formatDate(gallery.galleryDate)
  const place = document.createElement('span')
  place.textContent = gallery.placeName
  text.append(title, date, place)
  body.append(text)
  if (onClose) body.append(closeButton(onClose))
  return body
}

function bindTooltip(entry, permanent) {
  entry.marker.unbindTooltip()
  entry.marker.bindTooltip(() => tooltipContent(entry.gallery, permanent ? () => entry.marker.closeTooltip() : null), {
    direction: 'top',
    opacity: 1,
    className: `pin-tip pin-tip--stage pin-tip--gallery${permanent ? ' pin-tip--closable' : ''}`,
    permanent,
    interactive: permanent,
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
    marker.on('click', () => {
      emit('select', gallery.id)
      if (gallery.id === props.selectedId && !marker.isTooltipOpen()) marker.openTooltip() // rouvre l'infobulle fermée à la main
    })
    marker.addTo(layer)
    const entry = { marker, gallery }
    bindTooltip(entry, false)
    describe(entry)
    markers.set(gallery.id, entry)
  }
  fitInitial()
  applySelection(props.selectedId, null)
}

// Cadrage d'ouverture (voir openingFrame.js) : centré sur la dernière position connue, zoom selon sa source et son ancienneté.
function applyOpeningFrame(frame, animate) {
  let zoom = frame.zoom
  if (frame.fit) {
    const fitted = map.getBoundsZoom(L.latLngBounds(frame.fit.points), false, L.point(40, 40))
    zoom = Math.min(frame.fit.maxZoom, Math.max(frame.fit.minZoom, Math.floor(fitted)))
  }
  map.setView(frame.center, zoom, { animate })
}

// Cadrage initial, une seule fois : sur la dernière position connue s'il y en a une (et si l'adresse ne désigne pas une galerie
// ou une étape) ; sinon tout ce qui est connu (galeries, étapes et tracé) tient à l'écran.
function fitInitial() {
  if (fitted || !map || !props.galleriesLoaded) return
  const frame = props.selectedId == null && props.selectedStageId == null ? openingFrame(props.track) : null
  if (frame) {
    fitted = true
    applyOpeningFrame(frame, false)
    return
  }
  const points = props.galleries.map((g) => [g.latitude, g.longitude])
  for (const stage of props.stages) points.push(stageAnchor(stage))
  for (const segment of props.track.segments) points.push(...segment.points)
  if (points.length === 0) return
  fitted = true
  map.fitBounds(L.latLngBounds(points), { padding: [40, 40], maxZoom: 10, animate: false })
}

// Bouton « Recentrer sur la position » : revient au cadrage d'ouverture (masqué tant qu'il n'y a pas de dernière position).
let recenterControl = null

function recenter() {
  const frame = openingFrame(props.track)
  if (frame) applyOpeningFrame(frame, !prefersReducedMotion())
}

function updateRecenter() {
  const container = recenterControl?.getContainer()
  if (container) container.hidden = !openingFrame(props.track)
}

function addRecenterControl() {
  const Recenter = L.Control.extend({
    options: { position: 'topleft' },
    onAdd() {
      const container = L.DomUtil.create('div', 'leaflet-bar leaflet-control map-recenter')
      const button = L.DomUtil.create('a', 'map-recenter__button', container)
      button.href = '#'
      button.setAttribute('role', 'button')
      button.setAttribute('title', 'Recentrer sur la position')
      button.setAttribute('aria-label', 'Recentrer sur la position')
      button.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="3.5" /><path d="M12 2.5v4M12 17.5v4M2.5 12h4M17.5 12h4" /></svg>'
      L.DomEvent.disableClickPropagation(container)
      L.DomEvent.on(button, 'click', (event) => {
        L.DomEvent.stop(event)
        recenter()
      })
      return container
    },
  })
  recenterControl = new Recenter().addTo(map)
  updateRecenter()
}

// Étapes : une ligne d'itinéraire (sous le tracé GPS), des liaisons pointillées et un badge par étape (au milieu du trajet pour un trajet).
function drawStages() {
  if (!map) return
  stageLayer.clearLayers()
  routeLayer.clearLayers()
  stageEntries.clear()
  arrows = []
  const color = itineraryColor()

  // Liaisons entre étapes consécutives éloignées de plus d'1 km (pointillés fins).
  for (const link of itineraryLinks(props.stages)) {
    const future = isFuture(link.next, props.today)
    L.polyline(link.line, { color, weight: 3, opacity: future ? 0.3 : 0.7, dashArray: '2 8', lineCap: 'round', interactive: false }).addTo(routeLayer)
    addArrow(link.line, 0.5, { small: true, future })
  }

  for (const stage of props.stages) {
    const future = isFuture(stage, props.today)
    const label = STAGE_KINDS[stage.kind]?.label ?? 'Étape'
    const entry = { stage, future, line: null, arrow: null, marker: null }

    const points = stageLine(stage)
    if (points) {
      const style = STAGE_KINDS[stage.kind] ?? STAGE_KINDS.PLACE
      entry.line = L.polyline(points, { color: stageColor(stage.kind), weight: style.weight, dashArray: style.dash,  opacity: future ? 0.3 : 0.6, lineCap: 'round', lineJoin: 'round', smoothFactor: 1.5 })
      entry.line.on('click', () => emit('select-stage', stage.id))
      entry.line.addTo(routeLayer)
      entry.arrow = addArrow(points, 0.7, { future, kind: stage.kind })
    }

    entry.marker = L.marker(stageAnchor(stage), { icon: stageIcon(stage.kind, { future }), keyboard: true, title: '', zIndexOffset: -500 })
    entry.marker.on('click', () => {
      emit('select-stage', stage.id)
      if (stage.id === props.selectedStageId && !entry.marker.isTooltipOpen()) entry.marker.openTooltip() // rouvre l'infobulle fermée à la main
    })
    entry.marker.addTo(stageLayer)
    stageEntries.set(stage.id, entry)
    styleStage(entry, stage.id === props.selectedStageId, label)
  }
  // Les lignes d'itinéraire (redessinées après coup) doivent rester SOUS le tracé GPS.
  trackLayer.eachLayer((layer) => layer.bringToFront?.())
}

// Pointe de flèche à la fraction f de la ligne, orientée dans le sens du voyage.
function addArrow(points, f, { small = false, future = false, kind = null } = {}) {
  const { point, from, to } = pointAlong(points, f)
  const marker = L.marker(point, { icon: arrowIcon(0, { small, future, kind }), interactive: false, keyboard: false, zIndexOffset: -1000 })
  marker.addTo(routeLayer)
  const arrow = { marker, from, to, small, future, kind }
  arrows.push(arrow)
  orientArrow(arrow)
  return arrow
}

// L'angle dépend de la projection : on le recalcule à chaque changement de zoom.
function orientArrow(arrow) {
  const a = map.latLngToLayerPoint(arrow.from)
  const b = map.latLngToLayerPoint(arrow.to)
  const angle = a.equals(b) ? 0 : (Math.atan2(b.y - a.y, b.x - a.x) * 180) / Math.PI
  arrow.marker.setIcon(arrowIcon(angle, { small: arrow.small, future: arrow.future, kind: arrow.kind }))
}

function styleStage(entry, selected, label = STAGE_KINDS[entry.stage.kind]?.label ?? 'Étape') {
  const { stage, future } = entry
  entry.marker.setIcon(stageIcon(stage.kind, { future, selected }))
  entry.marker.setZIndexOffset(selected ? 900 : -500)
  entry.marker.unbindTooltip()
  entry.marker.bindTooltip(() => stageTooltip(stage, future, selected ? () => entry.marker.closeTooltip() : null), {
    direction: 'top',
    opacity: 1,
    className: `pin-tip pin-tip--stage${selected ? ' pin-tip--closable' : ''}`,
    permanent: selected,
    interactive: selected,
  })
  if (selected) entry.marker.openTooltip()
  entry.line?.setStyle({ weight: selected ? (STAGE_KINDS[stage.kind]?.weight ?? 7) + 3 : (STAGE_KINDS[stage.kind]?.weight ?? 7), opacity: selected ? 0.95 : future ? 0.3 : 0.6 })
  const markerElement = entry.marker.getElement()
  if (markerElement) {
    markerElement.setAttribute('role', 'button')
    markerElement.setAttribute('aria-label', `${label} : ${stage.name}${future ? ' (à venir)' : ''}`)
  }
}

function applyStageSelection(id, previousId) {
  if (!map) return
  const previous = previousId != null ? stageEntries.get(previousId) : null
  if (previous) styleStage(previous, false)
  const current = id != null ? stageEntries.get(id) : null
  if (!current) return
  styleStage(current, true)
  const animate = !prefersReducedMotion()
  if (current.line) map.fitBounds(current.line.getBounds(), { padding: [60, 60], maxZoom: 9, animate })
  else map.setView(current.marker.getLatLng(), Math.max(map.getZoom(), 6), { animate })
}

// Deux lignes : le nom (lieu ou trajet), puis la date (« · à venir » si l'étape n'a pas commencé).
function stageTooltip(stage, future, onClose = null) {
  const body = document.createElement('div')
  body.className = 'stage-tip'
  const title = document.createElement('strong')
  title.textContent = stage.name
  const date = document.createElement('span')
  date.textContent = `${formatDateRange(stage.startDate, stage.endDate)}${future ? ' · à venir' : ''}`
  body.append(title, date)
  if (onClose) body.append(closeButton(onClose))
  return body
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
    const late = isLate(props.track)
    const marker = L.marker([last.latitude, last.longitude], {
      icon: liveIcon(last.source, { heading: lastHeading(props.track), late }),
      keyboard: false,
      title: '',
      zIndexOffset: 300,
    })
    marker.bindTooltip(() => liveTooltip(last, late), { direction: 'top', className: 'pin-tip pin-tip--stage' }).addTo(trackLayer)
    marker.getElement()?.setAttribute('role', 'img')
    marker.getElement()?.setAttribute('aria-label', `${TRACK_SOURCES[last.source].label} : dernière position connue${late ? ' (en retard)' : ''}`)
  }
  fitInitial()
  updateRecenter()
}

// Trois lignes au plus : la source, l'ancienneté, et « en retard » le cas échéant.
function liveTooltip(last, late) {
  const body = document.createElement('div')
  body.className = 'stage-tip'
  const title = document.createElement('strong')
  title.textContent = `Dernière position · ${TRACK_SOURCES[last.source].label}`
  const age = document.createElement('span')
  age.textContent = timeAgo(last.recordedAt)
  body.append(title, age)
  if (late) {
    const warning = document.createElement('span')
    warning.textContent = 'En retard : pas de nouvelle position'
    body.append(warning)
  }
  return body
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
  // Ordre d'empilement : itinéraire, puis tracé GPS (au-dessus), puis marqueurs.
  routeLayer = L.layerGroup().addTo(map)
  trackLayer = L.layerGroup().addTo(map)
  stageLayer = L.layerGroup().addTo(map)
  layer = L.layerGroup().addTo(map)

  // La carte doit être recalculée quand son conteneur change de taille (fenêtre, rotation du téléphone...).
  observer = new ResizeObserver(() => map.invalidateSize())
  observer.observe(element.value)

  map.on('zoomend', () => arrows.forEach(orientArrow))
  addRecenterControl()
  render()
  drawStages()
  applyStageSelection(props.selectedStageId, null)
  drawTrack()
})

onBeforeUnmount(() => {
  observer?.disconnect()
  map?.remove()
  map = null
  recenterControl = null
  markers.clear()
})

watch(() => props.galleries, render)
watch(() => props.track, drawTrack)
watch(
  () => props.stages,
  () => {
    drawStages()
    fitInitial()
    applyStageSelection(props.selectedStageId, null)
  },
)
watch(() => props.today, () => { drawStages(); applyStageSelection(props.selectedStageId, null) })
watch(() => props.selectedStageId, (id, previous) => applyStageSelection(id, previous))
watch(() => props.galleriesLoaded, fitInitial)
watch(() => props.selectedId, (id, previous) => applySelection(id, previous))
</script>

<template>
  <div ref="element" class="travel-map"></div>
</template>
