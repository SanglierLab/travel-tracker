// Types d'étape du voyage et leurs pictos (tracés 24 × 24, au trait). Partagé par l'administration, la carte et le fil des photos,
// pour que chaque type ait partout le même picto. Les albums photo ont leur propre picto (un appareil photo), distinct de ceux-ci.

export const STAGE_KINDS = {
  PLACE: { label: 'Lieu fixe', icon: 'M12 21s-6-5.2-6-10a6 6 0 1112 0c0 4.8-6 10-6 10zM12 8.5a2.5 2.5 0 100 5 2.5 2.5 0 000-5z' },
  TRAIN: { label: 'Train', icon: 'M7 3h10a2 2 0 012 2v9a3 3 0 01-3 3H8a3 3 0 01-3-3V5a2 2 0 012-2zM5 10h14M8 20l-2 2M16 20l2 2M9 14h.01M15 14h.01' },
  PLANE: { label: 'Avion', icon: 'M3 13l18-8-6 14-3-6-6-2zM12 13l9-8' },
  BOAT: { label: 'Bateau', icon: 'M12 3v11M12 4l6 9h-6M4 16h16l-2 4H6z' },
}

// Apparence du tracé (et de la pastille) de chaque type : la couleur vient du thème (--stage-*), le trait est fixe pour
// qu'on distingue les types même sans couleur. Même principe que TRACK_SOURCES (trackStyle.js).
const STAGE_LINES = {
  PLACE: { cssVar: '--stage-place', fallback: '#6d4c41', dash: null, weight: 7 },
  TRAIN: { cssVar: '--stage-train', fallback: '#c2185b', dash: null, weight: 7 }, // trait plein
  PLANE: { cssVar: '--stage-plane', fallback: '#1e63c9', dash: '1 13', weight: 7 }, // gros points
  BOAT: { cssVar: '--stage-boat', fallback: '#0e8f9a', dash: '20 12', weight: 7 }, // longs tirets
}
for (const kind of Object.keys(STAGE_LINES)) Object.assign(STAGE_KINDS[kind], STAGE_LINES[kind])

/** Couleur CSS actuelle d'un type d'étape (lue au moment du dessin, donc à jour avec le thème). */
export function stageColor(kind) {
  const { cssVar, fallback } = STAGE_KINDS[kind] ?? STAGE_KINDS.PLACE
  return getComputedStyle(document.documentElement).getPropertyValue(cssVar).trim() || fallback
}

/** Classe CSS qui définit --stage-color pour ce type (picto, pastille, flèche). */
export function stageKindClass(kind) {
  return `stage-kind--${String(kind ?? 'PLACE').toLowerCase()}`
}

export const KIND_ORDER = ['PLACE', 'TRAIN', 'PLANE', 'BOAT']

/** Un trajet a une position de départ et une d'arrivée ; un lieu fixe une seule position. */
export function isTrip(kind) {
  return kind !== 'PLACE'
}
