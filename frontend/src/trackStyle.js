// Apparence des tracés selon leur source. Les couleurs viennent du thème (variables CSS --track-*), donc
// elles changent avec lui ; les styles de trait, eux, sont fixes pour qu'on distingue les sources même sans couleur.
export const TRACK_SOURCES = {
  DEVICE: { label: 'Téléphone', cssVar: '--track-device', fallback: '#e0561b', dash: null, weight: 4 }, // trait plein
  ADSB: { label: 'Avion', cssVar: '--track-adsb', fallback: '#6a3fc4', dash: '1 9', weight: 4 }, // pointillés
  AIS: { label: 'Bateau', cssVar: '--track-ais', fallback: '#1f8a4c', dash: '14 10', weight: 4 }, // tirets espacés
}

/** Couleur CSS actuelle d'une source (lue au moment du dessin, donc à jour avec le thème). */
export function trackColor(source) {
  const { cssVar, fallback } = TRACK_SOURCES[source]
  return getComputedStyle(document.documentElement).getPropertyValue(cssVar).trim() || fallback
}
