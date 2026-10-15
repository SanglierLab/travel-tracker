// Calculs géographiques simples sur les étapes (sans dépendance, testables seuls).

/** Date du jour au format « 2026-10-15 » dans le fuseau du visiteur. */
export function todayIso(now = new Date()) {
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}-${month}-${day}`
}

/** « 2026-10-15 » + 7 jours = « 2026-10-22 » (calendrier local, sans passage par un fuseau). */
export function addDays(iso, days) {
  const [year, month, day] = iso.split('-').map(Number)
  return todayIso(new Date(year, month - 1, day + days))
}

/** Étape à venir qui commence dans `days` jours ou moins : on la montre en tête du fil plutôt que dans le bloc replié. */
export function isImminent(stage, today, days) {
  return isFuture(stage, today) && stage.startDate <= addDays(today, days)
}

/** Une étape est « à venir » tant que sa date de début n'est pas atteinte (comparaison de dates ISO, sans fuseau). */
export function isFuture(stage, today = todayIso()) {
  return stage.startDate > today
}

// Distance approchée en degrés (suffit pour trouver le milieu d'un tracé), longitudes ramenées dans ±180 d'écart.
function step(a, b) {
  const dLat = b[0] - a[0]
  let dLon = b[1] - a[1]
  if (dLon > 180) dLon -= 360
  if (dLon < -180) dLon += 360
  return [dLat, dLon]
}

function length(a, b) {
  const [dLat, dLon] = step(a, b)
  const k = Math.cos(((a[0] + b[0]) / 2) * (Math.PI / 180))
  return Math.hypot(dLat, dLon * k)
}

/** Point situé à la fraction f (0 à 1) de la longueur d'une suite de points [lat, lon], avec le segment qui le porte. */
export function pointAlong(points, f) {
  if (points.length === 1) return { point: points[0], from: points[0], to: points[0] }
  let total = 0
  for (let i = 1; i < points.length; i++) total += length(points[i - 1], points[i])
  if (total === 0) return { point: points[0], from: points[0], to: points[points.length - 1] }
  let remaining = total * f
  for (let i = 1; i < points.length; i++) {
    const segment = length(points[i - 1], points[i])
    if (remaining <= segment) {
      const ratio = segment === 0 ? 0 : remaining / segment
      const [dLat, dLon] = step(points[i - 1], points[i])
      return {
        point: [points[i - 1][0] + dLat * ratio, points[i - 1][1] + dLon * ratio],
        from: points[i - 1],
        to: points[i],
      }
    }
    remaining -= segment
  }
  const last = points.length - 1
  return { point: points[last], from: points[last - 1], to: points[last] }
}

/** Point situé à mi-longueur d'une suite de points [lat, lon]. */
export function midpointOfPath(points) {
  return pointAlong(points, 0.5).point
}

/** Rend les longitudes continues (ex. 179, -179 devient 179, 181) : une ligne qui traverse la ligne de changement de date ne fait pas le tour du monde. */
export function unwrapLongitudes(points) {
  const result = []
  let offset = 0
  for (let i = 0; i < points.length; i++) {
    if (i > 0) {
      const delta = points[i][1] + offset - result[i - 1][1]
      if (delta > 180) offset -= 360
      else if (delta < -180) offset += 360
    }
    result.push([points[i][0], points[i][1] + offset])
  }
  return result
}

const EARTH_RADIUS_KM = 6371
const toRad = (deg) => deg * (Math.PI / 180)
const toDeg = (rad) => rad * (180 / Math.PI)

/** Distance à vol d'oiseau en kilomètres. */
export function distanceKm(a, b) {
  const dLat = toRad(b[0] - a[0])
  const dLon = toRad(b[1] - a[1])
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(toRad(a[0])) * Math.cos(toRad(b[0])) * Math.sin(dLon / 2) ** 2
  return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1, Math.sqrt(h)))
}

/**
 * Cap de a vers b, en degrés (0 = nord, 90 = est, sens horaire), tel qu'on le voit sur la carte (projection de Mercator :
 * c'est l'angle à l'écran, pas celui du grand cercle). Les longitudes peuvent différer de plus de 180° (ligne de changement de date).
 */
export function headingDegrees(a, b) {
  let dLon = b[1] - a[1]
  dLon -= 360 * Math.round(dLon / 360)
  const mercatorY = (lat) => Math.log(Math.tan(Math.PI / 4 + toRad(Math.max(-85, Math.min(85, lat))) / 2))
  const degrees = (Math.atan2(toRad(dLon), mercatorY(b[0]) - mercatorY(a[0])) * 180) / Math.PI
  return (degrees + 360) % 360
}

/** Arc de grand cercle de a à b (le plus court chemin, comme le suit un avion), en `count` points. */
export function greatCircle(a, b, count = 48) {
  const [lat1, lon1, lat2, lon2] = [toRad(a[0]), toRad(a[1]), toRad(b[0]), toRad(b[1])]
  const d = 2 * Math.asin(Math.min(1, Math.sqrt(Math.sin((lat2 - lat1) / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin((lon2 - lon1) / 2) ** 2)))
  if (d < 1e-9) return [a, b]
  const points = []
  for (let i = 0; i <= count; i++) {
    const f = i / count
    const A = Math.sin((1 - f) * d) / Math.sin(d)
    const B = Math.sin(f * d) / Math.sin(d)
    const x = A * Math.cos(lat1) * Math.cos(lon1) + B * Math.cos(lat2) * Math.cos(lon2)
    const y = A * Math.cos(lat1) * Math.sin(lon1) + B * Math.cos(lat2) * Math.sin(lon2)
    const z = A * Math.sin(lat1) + B * Math.sin(lat2)
    points.push([toDeg(Math.atan2(z, Math.hypot(x, y))), toDeg(Math.atan2(y, x))])
  }
  points[0] = a
  points[points.length - 1] = b
  return unwrapLongitudes(points)
}

// Droite départ → arrivée en `count` + 1 points (longitudes rendues continues).
function straightLine(start, end, count) {
  const [a, b] = unwrapLongitudes([start, end])
  return Array.from({ length: count + 1 }, (_, n) => [a[0] + ((b[0] - a[0]) * n) / count, a[1] + ((b[1] - a[1]) * n) / count])
}

/** Courbure des trajets sans GPX : le sommet de l'arc s'écarte de la corde de cette fraction de sa longueur. */
export const ARC_BULGE = 0.15

/**
 * Arc entre deux positions : la ligne de base (grand cercle pour un avion, droite sinon) est écartée de la corde,
 * toujours sur la DROITE du sens de marche. Un aller et son retour se courbent donc de part et d'autre : ils ne se superposent pas.
 */
export function arcBetween(start, end, { greatCircleBase = false, count = 48 } = {}) {
  const base = greatCircleBase ? greatCircle(start, end, count) : straightLine(start, end, count)
  const last = base[base.length - 1]
  const k = Math.max(0.2, Math.cos(toRad((start[0] + last[0]) / 2))) // les degrés de longitude sont plus courts loin de l'équateur
  const cx = (last[1] - base[0][1]) * k
  const cy = last[0] - base[0][0]
  const chord = Math.hypot(cx, cy)
  if (chord < 1e-9) return [start, end]
  const [rightX, rightY] = [cy / chord, -cx / chord]
  return base.map(([lat, lon], i) => {
    const offset = ARC_BULGE * chord * Math.sin(Math.PI * (i / (base.length - 1)))
    return [lat + offset * rightY, lon + (offset * rightX) / k]
  })
}

/**
 * Ligne d'itinéraire d'une étape : null pour un lieu fixe ; le tracé GPX s'il y en a un ; sinon un arc de cercle
 * entre le départ et l'arrivée (porté par le grand cercle pour un avion).
 */
export function stageLine(stage) {
  if (stage.kind === 'PLACE' || stage.endLatitude == null || stage.endLongitude == null) return null
  if (stage.path?.length >= 2) return unwrapLongitudes(stage.path)
  return arcBetween([stage.startLatitude, stage.startLongitude], [stage.endLatitude, stage.endLongitude], { greatCircleBase: stage.kind === 'PLANE' })
}

/** Position du marqueur d'une étape : le lieu lui-même, ou le milieu de sa ligne pour un trajet. */
export function stageAnchor(stage) {
  const line = stageLine(stage)
  return line ? midpointOfPath(line) : [stage.startLatitude, stage.startLongitude]
}

/** Où l'étape se termine : l'arrivée d'un trajet, ou le lieu lui-même. */
export function stageEnd(stage) {
  return stage.kind !== 'PLACE' && stage.endLatitude != null ? [stage.endLatitude, stage.endLongitude] : [stage.startLatitude, stage.startLongitude]
}

export const LINK_MIN_KM = 1

/** Liaisons entre étapes consécutives (dans l'ordre de l'itinéraire) dont l'arrivée et le départ sont distants de plus d'1 km. */
export function itineraryLinks(stages) {
  const links = []
  for (let i = 0; i + 1 < stages.length; i++) {
    const from = stageEnd(stages[i])
    const to = [stages[i + 1].startLatitude, stages[i + 1].startLongitude]
    if (distanceKm(from, to) > LINK_MIN_KM) links.push({ from, to, line: unwrapLongitudes([from, to]), next: stages[i + 1] })
  }
  return links
}
