package fr.sanglierlab.traveltracker.track;

/**
 * Réponse à GPSLogger (toujours HTTP 200) : {@code status} vaut « created », « duplicate » (point déjà reçu)
 * ou « ignored » (point écarté, avec la raison : « accuracy » ou « no-fix »).
 */
public record TrackPointResponse(String status, String reason) {
}
