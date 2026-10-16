package fr.sanglierlab.traveltracker.track;

import java.time.LocalDateTime;

/** Nombre de points d'une source et heure (UTC) du dernier. */
public record SourceStats(TrackSource source, Long count, LocalDateTime lastRecordedAt) {
}
