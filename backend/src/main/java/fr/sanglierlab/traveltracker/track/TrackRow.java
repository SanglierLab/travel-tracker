package fr.sanglierlab.traveltracker.track;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Ligne légère lue en base pour construire le tracé (pas d'entité complète : il peut y avoir des dizaines de milliers de points). */
public record TrackRow(TrackSource source, Long tripId, BigDecimal latitude, BigDecimal longitude, LocalDateTime recordedAt) {

    public Instant instant() {
        return recordedAt.toInstant(ZoneOffset.UTC);
    }
}
