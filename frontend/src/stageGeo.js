// Calculs géographiques simples sur les étapes (sans dépendance, testables seuls).

/** Date du jour au format « 2026-10-15 » dans le fuseau du visiteur. */
export function todayIso(now = new Date()) {
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}-${month}-${day}`
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

function normalizeLon(lon) {
  return ((((lon + 180) % 360) + 360) % 360) - 180
}

/** Point situé à mi-longueur d'une suite de points [lat, lon]. */
export function midpointOfPath(points) {
  if (points.length === 1) return points[0]
  let total = 0
  for (let i = 1; i < points.length; i++) total += length(points[i - 1], points[i])
  if (total === 0) return points[0]
  let remaining = total / 2
  for (let i = 1; i < points.length; i++) {
    const segment = length(points[i - 1], points[i])
    if (remaining <= segment) {
      const ratio = segment === 0 ? 0 : remaining / segment
      const [dLat, dLon] = step(points[i - 1], points[i])
      return [points[i - 1][0] + dLat * ratio, normalizeLon(points[i - 1][1] + dLon * ratio)]
    }
    remaining -= segment
  }
  return points[points.length - 1]
}

/** Position du marqueur d'une étape : le lieu lui-même, ou le milieu du trajet (GPX si présent, sinon départ → arrivée). */
export function stageAnchor(stage) {
  if (stage.kind === 'PLACE' || stage.endLatitude == null || stage.endLongitude == null) {
    return [stage.startLatitude, stage.startLongitude]
  }
  const points = stage.path?.length >= 2 ? stage.path : [[stage.startLatitude, stage.startLongitude], [stage.endLatitude, stage.endLongitude]]
  return midpointOfPath(points)
}
