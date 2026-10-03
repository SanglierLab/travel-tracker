// Éléments Leaflet communs à la carte publique et au sélecteur de position de l'admin.
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

export { L }

const TILES_URL = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png'
const ATTRIBUTION =
  '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a>'

const PIN_PATH = 'M16 1C7.7 1 1 7.6 1 15.7 1 26.5 16 39 16 39s15-12.5 15-23.3C31 7.6 24.3 1 16 1z'

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
    html: `<svg viewBox="0 0 32 40" aria-hidden="true"><path d="${PIN_PATH}" /><circle cx="16" cy="15.5" r="5.5" /></svg>`,
    iconSize: [width, height],
    iconAnchor: [width / 2, height],
    tooltipAnchor: [0, -height + 4],
  })
}
