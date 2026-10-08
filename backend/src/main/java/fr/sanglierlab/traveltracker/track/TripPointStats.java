package fr.sanglierlab.traveltracker.track;

import java.time.LocalDateTime;

/** Nombre de points d'un trajet suivi et heure (UTC) du dernier. */
public record TripPointStats(Long tripId, Long count, LocalDateTime lastRecordedAt) {
}
