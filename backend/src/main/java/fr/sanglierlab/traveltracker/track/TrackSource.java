package fr.sanglierlab.traveltracker.track;

/** Origine d'un point de route : chaque source est dessinée différemment sur la carte. */
public enum TrackSource {
    /** Téléphone du voyageur (GPSLogger). */
    DEVICE,
    /** Transport aérien (ADS-B) : positions des vols suivis. */
    ADSB,
    /** Transport maritime (AIS) : positions des croisières suivies. */
    AIS
}
