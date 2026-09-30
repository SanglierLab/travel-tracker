package fr.sanglierlab.travel.trip;

/**
 * Cycle de vie d'un trajet suivi.
 *
 *   PLANIFIÉ ──(heure de départ atteinte)──▶ EN COURS ──(fin détectée)──▶ TERMINÉ
 *
 * Le passage en cours déclenche l'interrogation périodique de la source de
 * données ; la fin l'interrompt. Un trajet terminé conserve sa trace, qui
 * reste affichée sur la carte.
 */
public enum TripStatus {

    PLANNED("Planifié"),
    ACTIVE("En cours"),
    FINISHED("Terminé");

    private final String label;

    TripStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
