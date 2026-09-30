package fr.sanglierlab.travel.trace.dto;

import fr.sanglierlab.travel.trace.TraceSource;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Position transmise par le traceur mobile.
 *
 * La source est facultative et vaut {@code DEVICE} par défaut : l'outil
 * embarqué n'a ainsi qu'à envoyer l'essentiel. De même, un horodatage absent
 * est remplacé par l'instant de réception.
 */
public record IngestPointForm(

        TraceSource source,

        Instant measuredAt,

        @NotNull(message = "La latitude est obligatoire")
        @DecimalMin(value = "-90", message = "Latitude hors limites")
        @DecimalMax(value = "90", message = "Latitude hors limites")
        Double latitude,

        @NotNull(message = "La longitude est obligatoire")
        @DecimalMin(value = "-180", message = "Longitude hors limites")
        @DecimalMax(value = "180", message = "Longitude hors limites")
        Double longitude,

        Double altitudeM,
        Double speedKmh,
        Double headingDeg,
        Double accuracyM,

        /** Rattachement explicite à un trajet, pour les sources ADS-B et AIS. */
        Long tripId
) {

    public TraceSource sourceOrDefault() {
        return source == null ? TraceSource.DEVICE : source;
    }

    public Instant measuredAtOrNow() {
        return measuredAt == null ? Instant.now() : measuredAt;
    }
}
