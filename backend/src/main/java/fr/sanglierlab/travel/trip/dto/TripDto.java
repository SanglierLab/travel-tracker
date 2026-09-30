package fr.sanglierlab.travel.trip.dto;

import fr.sanglierlab.travel.trip.Trip;
import fr.sanglierlab.travel.trip.TripStatus;
import fr.sanglierlab.travel.trip.TripType;

import java.time.Instant;

/** Trajet tel que l'API l'expose. */
public record TripDto(
        Long id,
        TripType type,
        String identifier,
        String label,
        Instant scheduledStart,
        TripStatus status,
        String statusLabel,
        Instant startedAt,
        Instant finishedAt,
        Instant lastPolledAt,
        String color,
        long pointCount
) {

    public static TripDto from(Trip trip, long pointCount) {
        return new TripDto(
                trip.getId(),
                trip.getType(),
                trip.getIdentifier(),
                trip.displayLabel(),
                trip.getScheduledStart(),
                trip.getStatus(),
                trip.getStatus().label(),
                trip.getStartedAt(),
                trip.getFinishedAt(),
                trip.getLastPolledAt(),
                trip.getColor(),
                pointCount);
    }
}
