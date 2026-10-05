const dateFormat = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'long', year: 'numeric' })

/** « 2026-10-10 » -> « 10 octobre 2026 ». Pas de passage par Date(string) : évite tout décalage de fuseau. */
export function formatDate(iso) {
  const [year, month, day] = iso.split('-').map(Number)
  return dateFormat.format(new Date(year, month - 1, day))
}

const sizeFormat = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 1 })

/** 4404019 -> « 4,2 Mo » */
export function formatSize(bytes) {
  if (!bytes) return ''
  if (bytes < 1024 * 1024) return `${sizeFormat.format(bytes / 1024)} Ko`
  if (bytes < 1024 * 1024 * 1024) return `${sizeFormat.format(bytes / (1024 * 1024))} Mo`
  return `${sizeFormat.format(bytes / (1024 * 1024 * 1024))} Go`
}

const dateTimeFormat = new Intl.DateTimeFormat('fr-FR', { dateStyle: 'medium', timeStyle: 'short' })

/** Date et heure ISO (UTC) affichées dans le fuseau du visiteur : « 4 oct. 2026, 12:30 ». */
export function formatDateTime(iso) {
  return dateTimeFormat.format(new Date(iso))
}

/** « à l'instant », « il y a 12 min », « il y a 3 h », puis la date complète au-delà d'une journée. */
export function timeAgo(iso, now = Date.now()) {
  const minutes = Math.floor((now - new Date(iso).getTime()) / 60000)
  if (minutes < 1) return "à l'instant"
  if (minutes < 60) return `il y a ${minutes} min`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `il y a ${hours} h`
  return `le ${formatDateTime(iso)}`
}
