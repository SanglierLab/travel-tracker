package fr.sanglierlab.traveltracker.track;

public enum TripType {
    /** Vol (suivi ADS-B). */
    PLANE,
    /** Croisière ou traversée en bateau (suivi AIS, MMSI du navire). */
    BOAT,
    /** Session Garmin LiveTrack (montre) : identifiant de session et jeton du lien de partage. */
    LIVETRACK
}
