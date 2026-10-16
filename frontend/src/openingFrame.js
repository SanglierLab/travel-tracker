import { distanceKm } from './stageGeo'
import { TRACK_SOURCES, isLate } from './trackStyle'

// Cadrage d'ouverture de la carte, d'après la dernière position connue (sa source et son ancienneté).
//
//   frais          : pas en retard pour sa source
//   en retard      : en retard, mais de moins de 6 h
//   ancien         : plus de 6 h (même seuil que la coupure des traces du téléphone)
//
// Niveaux de zoom par famille de source ; l'ordre est [frais, en retard, ancien].
//   à pied / en voiture (téléphone, montre) : 13, 12, 6
//   bateau                                  : 9, 8, 5
//   avion                                   : cadré sur la trace du vol (zoom 5 à 7), puis 5, 4
export const OLD_AFTER_MS = 6 * 3600 * 1000
const ZOOMS = {
  DEVICE: [13, 12, 6],
  GARMIN: [13, 12, 6],
  AIS: [9, 8, 5],
  ADSB: [null, 5, 4],
}
// Longueur de trace montrée autour d'une position fraîche (km), et zoom maximal du cadrage.
const CONTEXT_KM = { DEVICE: 3, GARMIN: 3, AIS: 30 }
const MAX_ZOOM = { DEVICE: 14, GARMIN: 14, AIS: 10 }
const PLANE_ZOOM = { min: 5, max: 7 }

/** Dernière portion du tracé (la dernière ligne de la même source), de la fin vers le début, sur au plus `km` kilomètres. */
function trailingPoints(track, source, km) {
  const segments = (track.segments ?? []).filter((segment) => segment.source === source && segment.points?.length)
  if (segments.length === 0) return []
  const points = segments[segments.length - 1].points
  const kept = [points[points.length - 1]]
  let length = 0
  for (let i = points.length - 2; i >= 0 && length < km; i--) {
    length += distanceKm(points[i], points[i + 1])
    kept.push(points[i])
  }
  return kept
}

/** Toute la ligne du dernier vol (un trajet suivi = une ligne). */
function lastFlight(track) {
  const segments = (track.segments ?? []).filter((segment) => segment.source === 'ADSB' && segment.points?.length)
  return segments.length > 0 ? segments[segments.length - 1].points : []
}

/**
 * Ajoute à chaque point son symétrique par rapport au centre : l'étendue ainsi formée est centrée sur la dernière position
 * (la longitude est ramenée dans ±180° pour ne pas tout étirer autour de l'antiméridien).
 */
export function mirrored(center, points) {
  const all = [center]
  for (const [lat, lon] of points) {
    let dLon = lon - center[1]
    if (dLon > 180) dLon -= 360
    if (dLon < -180) dLon += 360
    all.push([center[0] + (lat - center[0]), center[1] + dLon], [center[0] - (lat - center[0]), center[1] - dLon])
  }
  return all
}

/**
 * Cadrage d'ouverture, ou null s'il n'y a pas de dernière position utilisable (la carte se cadre alors sur tout le voyage).
 * Résultat : { center: [lat, lon], zoom, tier, fit } où `fit` (ou null) donne des points à faire tenir à l'écran, centrés sur
 * `center`, avec le zoom borné à [minZoom, maxZoom] ; sans `fit`, le zoom est `zoom`.
 */
export function openingFrame(track, now = Date.now()) {
  const last = track?.last
  const info = last && TRACK_SOURCES[last.source]
  if (!info) return null
  const age = now - new Date(last.recordedAt).getTime()
  const tier = Number.isFinite(age) && age > OLD_AFTER_MS ? 2 : isLate(track, now) ? 1 : 0
  const center = [last.latitude, last.longitude]
  const zooms = ZOOMS[last.source]

  if (tier === 0 && last.source === 'ADSB') {
    const flight = lastFlight(track)
    return { center, zoom: 6, tier, fit: { points: mirrored(center, flight), minZoom: PLANE_ZOOM.min, maxZoom: PLANE_ZOOM.max } }
  }
  if (tier === 0) {
    return { center, zoom: zooms[0], tier, fit: { points: mirrored(center, trailingPoints(track, last.source, CONTEXT_KM[last.source])), minZoom: zooms[0], maxZoom: MAX_ZOOM[last.source] } }
  }
  return { center, zoom: zooms[tier], tier, fit: null }
}
