// Éléments Leaflet communs à la carte publique et au sélecteur de position de l'admin.
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { STAGE_KINDS, stageKindClass } from './stageKinds'
import { TRACK_SOURCES } from './trackStyle'

export { L }

const TILES_URL = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png'
const ATTRIBUTION =
  '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a>'

const PIN_PATH = 'M16 1C7.7 1 1 7.6 1 15.7 1 26.5 16 39 16 39s15-12.5 15-23.3C31 7.6 24.3 1 16 1z'

// Appareil photo (tracé au trait, 24 × 24) placé au centre de l'épingle : c'est ce qui distingue un album d'une étape.
const CAMERA_PATH = 'M4 7h3l1.5-2h7L17 7h3v11H4zM12 9.5a3.2 3.2 0 100 6.4 3.2 3.2 0 000-6.4z'

export function prefersReducedMotion() {
  return window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches ?? false
}

/**
 * Carte avec les tuiles OpenStreetMap. Peu d'animations : pas de fondu des tuiles ni d'animation des marqueurs,
 * et aucune animation de zoom si le visiteur a demandé de réduire les animations.
 */
export function createMap(element, options = {}) {
  const map = L.map(element, {
    fadeAnimation: false,
    markerZoomAnimation: false,
    zoomAnimation: !prefersReducedMotion(),
    ...options,
  })
  map.attributionControl.setPrefix(false)
  L.tileLayer(TILES_URL, { maxZoom: 19, attribution: ATTRIBUTION }).addTo(map)
  return map
}

/** Épingle dessinée en SVG (aucune image à charger), aux couleurs du thème. */
export function pinIcon(selected) {
  const [width, height] = selected ? [40, 50] : [30, 38]
  return L.divIcon({
    className: selected ? 'pin pin--selected' : 'pin',
    html: `<svg viewBox="0 0 32 40" aria-hidden="true"><path d="${PIN_PATH}" /><path class="pin__camera" transform="translate(8.6 8) scale(0.62)" d="${CAMERA_PATH}" /></svg>`,
    iconSize: [width, height],
    iconAnchor: [width / 2, height],
    tooltipAnchor: [0, -height + 4],
  })
}

/**
 * Marqueur d'étape : une pastille ronde (et non une épingle) avec le picto de son type.
 * Une étape à venir est une pastille creuse (classe stage-pin--future) ; la sélection ajoute un anneau, sans changer la taille.
 */
export function stageIcon(kind, { future = false, selected = false } = {}) {
  const icon = STAGE_KINDS[kind]?.icon ?? STAGE_KINDS.PLACE.icon
  return L.divIcon({
    className: `stage-pin ${stageKindClass(kind)}${future ? ' stage-pin--future' : ''}${selected ? ' stage-pin--selected' : ''}`,
    html: `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="${icon}" /></svg>`,
    iconSize: [28, 28],
    iconAnchor: [14, 14],
    tooltipAnchor: [0, -14],
  })
}

/**
 * Dernière position connue. Le téléphone : un point rond ; un avion ou un bateau : son picto seul (sans pastille),
 * orienté selon `heading` pour l'avion (le picto pointe vers le nord). `late` : style « en retard » (gris, estompé).
 */
export function liveIcon(source, { heading = null, late = false } = {}) {
  const info = TRACK_SOURCES[source]
  const kind = info?.kind
  const classes = `live-pin live-pin--${source.toLowerCase()}${late ? ' live-pin--late' : ''}`
  const style = `--live-color: var(${info?.cssVar ?? '--track-device'}, ${info?.fallback ?? '#e0561b'})`
  if (!kind) {
    return L.divIcon({ className: classes, html: `<span class="live-pin__dot" style="${style}"></span>`, iconSize: [19, 19], iconAnchor: [9.5, 9.5], tooltipAnchor: [0, -10] })
  }
  const rotation = kind === 'PLANE' && heading != null ? ` style="transform: rotate(${heading.toFixed(1)}deg)"` : ''
  const path = STAGE_KINDS[kind].icon
  return L.divIcon({
    className: classes,
    // Deux tracés superposés : un contour blanc (lisible sur n'importe quel fond de carte) puis le picto de la couleur du tracé.
    html: `<svg viewBox="0 0 24 24" aria-hidden="true" style="${style}"><g${rotation}><path class="live-pin__halo" d="${path}" /><path class="live-pin__glyph" d="${path}" /></g></svg>`,
    iconSize: [32, 32],
    iconAnchor: [16, 16],
    tooltipAnchor: [0, -14],
  })
}

/** Pointe de flèche posée sur la ligne d'itinéraire, tournée de `angle` degrés (0 = vers l'est, sens horaire). */
export function arrowIcon(angle, { small = false, future = false, kind = null } = {}) {
  const size = small ? 14 : 20
  return L.divIcon({
    className: `route-arrow${kind ? ' ' + stageKindClass(kind) : ''}${small ? ' route-arrow--small' : ''}${future ? ' route-arrow--future' : ''}`,
    html: `<svg viewBox="0 0 20 20" aria-hidden="true" style="transform: rotate(${angle.toFixed(1)}deg)"><path d="M3 3l15 7-15 7 4-7z" /></svg>`,
    iconSize: [size, size],
    iconAnchor: [size / 2, size / 2],
  })
}
