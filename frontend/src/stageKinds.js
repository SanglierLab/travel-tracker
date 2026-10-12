// Types d'étape du voyage et leurs pictos (tracés 24 × 24, au trait). Partagé par l'administration, la carte et le fil des photos,
// pour que chaque type ait partout le même picto. Les albums photo ont leur propre picto (un appareil photo), distinct de ceux-ci.

export const STAGE_KINDS = {
  PLACE: { label: 'Lieu fixe', icon: 'M12 21s-6-5.2-6-10a6 6 0 1112 0c0 4.8-6 10-6 10zM12 8.5a2.5 2.5 0 100 5 2.5 2.5 0 000-5z' },
  TRAIN: { label: 'Train', icon: 'M7 3h10a2 2 0 012 2v9a3 3 0 01-3 3H8a3 3 0 01-3-3V5a2 2 0 012-2zM5 10h14M8 20l-2 2M16 20l2 2M9 14h.01M15 14h.01' },
  PLANE: { label: 'Avion', icon: 'M3 13l18-8-6 14-3-6-6-2zM12 13l9-8' },
  BOAT: { label: 'Bateau', icon: 'M12 3v11M12 4l6 9h-6M4 16h16l-2 4H6z' },
}

export const KIND_ORDER = ['PLACE', 'TRAIN', 'PLANE', 'BOAT']

/** Un trajet a une position de départ et une d'arrivée ; un lieu fixe une seule position. */
export function isTrip(kind) {
  return kind !== 'PLACE'
}
