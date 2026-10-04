package fr.sanglierlab.traveltracker.track;

/** Origine d'un point de route : chaque source est dessinée différemment sur la carte. */
public enum TrackSource {
    /** Téléphone du voyageur (GPSLogger). */
    DEVICE,
    /** Transport aérien (ADS-B), à venir. */
    ADSB,
    /** Transport maritime (AIS), à venir. */
    AIS
}
