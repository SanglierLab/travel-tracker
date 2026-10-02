const dateFormat = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'long', year: 'numeric' })

/** « 2026-10-10 » -> « 10 octobre 2026 ». Pas de passage par Date(string) : évite tout décalage de fuseau. */
export function formatDate(iso) {
  const [year, month, day] = iso.split('-').map(Number)
  return dateFormat.format(new Date(year, month - 1, day))
}
