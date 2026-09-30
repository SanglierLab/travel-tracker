package fr.sanglierlab.travel.trace.dto;

import fr.sanglierlab.travel.trace.TracePoint;
import fr.sanglierlab.travel.trace.TraceSource;

import java.time.Instant;

/** Un point tel que l'API l'expose. */
public record TracePointDto(
        Long id,
        TraceSource source,
        Long tripId,
        Instant measuredAt,
        Instant receivedAt,
        double latitude,
        double longitude,
        Double altitudeM,
        Double speedKmh,
        Double headingDeg,
        Double accuracyM
) {

    public static TracePointDto from(TracePoint point) {
        return new TracePointDto(
                point.getId(),
                point.getSource(),
                point.getTrip() == null ? null : point.getTrip().getId(),
                point.getMeasuredAt(),
                point.getReceivedAt(),
                point.getLatitude(),
                point.getLongitude(),
                point.getAltitudeM(),
                point.getSpeedKmh(),
                point.getHeadingDeg(),
                point.getAccuracyM());
    }
}
