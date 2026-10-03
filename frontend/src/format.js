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
