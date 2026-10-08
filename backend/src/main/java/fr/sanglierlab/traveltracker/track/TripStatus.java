package fr.sanglierlab.traveltracker.track;

public enum TripStatus {
    /** Enregistré, suivi jamais démarré. */
    PLANNED,
    /** Suivi en cours (le batch récupère les positions). */
    ACTIVE,
    /** Suivi arrêté. */
    FINISHED
}
