package fr.sanglierlab.traveltracker.track;

/** Réponse à GPSLogger : « created » ou « duplicate » (point déjà reçu). Dans les deux cas, le statut HTTP est 200. */
public record TrackPointResponse(String status) {
}
