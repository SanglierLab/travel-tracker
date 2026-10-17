package fr.sanglierlab.traveltracker.track;

import java.time.LocalDateTime;

/** Nombre de points d'un trajet et heures (UTC) de son premier et de son dernier point. */
public record TripPointRange(Long tripId, Long count, LocalDateTime first, LocalDateTime last) {
}
