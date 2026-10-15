import { distanceKm, headingDegrees } from './stageGeo'

// Apparence des tracés selon leur source. Les couleurs viennent du thème (variables CSS --track-*), donc
// elles changent avec lui ; les styles de trait, eux, sont fixes pour qu'on distingue les sources même sans couleur.
// `kind` : type d'étape dont le picto marque la dernière position (null : simple point rond).
export const TRACK_SOURCES = {
  DEVICE: { label: 'Téléphone', cssVar: '--track-device', fallback: '#e0561b', dash: null, weight: 4, kind: null, lateAfterSeconds: 600 }, // trait plein
  ADSB: { label: 'Avion', cssVar: '--track-adsb', fallback: '#6a3fc4', dash: '1 9', weight: 4, kind: 'PLANE', lateAfterSeconds: 300 }, // pointillés
  AIS: { label: 'Bateau', cssVar: '--track-ais', fallback: '#1f8a4c', dash: '14 10', weight: 4, kind: 'BOAT', lateAfterSeconds: 1200 }, // tirets espacés
}

/** Couleur CSS actuelle d'une source (lue au moment du dessin, donc à jour avec le thème). */
export function trackColor(source) {
  const { cssVar, fallback } = TRACK_SOURCES[source]
  return getComputedStyle(document.documentElement).getPropertyValue(cssVar).trim() || fallback
}

/** Couleur de la ligne d'itinéraire (variable --itinerary du thème). */
export function itineraryColor() {
  return getComputedStyle(document.documentElement).getPropertyValue('--itinerary').trim() || '#c2185b'
}

/**
 * La dernière position est « en retard » quand aucune nouvelle position n'est arrivée depuis le délai de sa source
 * (`track.lateAfterSeconds`, réglé côté serveur ; valeurs par défaut ci-dessus si le serveur ne les donne pas).
 */
export function isLate(track, now = Date.now()) {
  const last = track?.last
  if (!last || !TRACK_SOURCES[last.source]) return false
  const limit = track.lateAfterSeconds?.[last.source] ?? TRACK_SOURCES[last.source].lateAfterSeconds
  return now - new Date(last.recordedAt).getTime() > limit * 1000
}

// En dessous de cette distance, deux points sont le même (bruit du GPS) : on ne peut pas en tirer un cap.
const MIN_HEADING_KM = 0.03

/**
 * Cap de la dernière position (0 = nord), déduit du dernier point du tracé de la même source qui en est assez éloigné ;
 * null s'il n'y en a pas (un seul point).
 */
export function lastHeading(track) {
  const last = track?.last
  if (!last) return null
  const here = [last.latitude, last.longitude]
  const segments = (track.segments ?? []).filter((segment) => segment.source === last.source)
  for (let s = segments.length - 1; s >= 0; s--) {
    const points = segments[s].points
    for (let i = points.length - 1; i >= 0; i--) {
      if (distanceKm(points[i], here) >= MIN_HEADING_KM) return headingDegrees(points[i], here)
    }
  }
  return null
}
