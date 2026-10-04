package fr.sanglierlab.traveltracker.track;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * Détermine l'heure d'un point reçu de GPSLogger.
 * <ol>
 *   <li>{@code time} : date ISO 8601 (variable {@code %TIME} de GPSLogger), ex. 2026-10-04T10:00:00.000Z</li>
 *   <li>sinon {@code timestamp} : secondes (ou millisecondes) depuis 1970 ({@code %TIMESTAMP})</li>
 *   <li>sinon l'heure de réception.</li>
 * </ol>
 * C'est bien l'heure du POINT qui compte, pas celle de l'envoi : GPSLogger peut renvoyer des points
 * en rafale après une coupure de réseau.
 */
public final class TrackTime {

    /** Au-delà, la valeur est en millisecondes (100 milliards de secondes = l'an 5138). */
    private static final long MILLIS_THRESHOLD = 100_000_000_000L;

    private TrackTime() {
    }

    /** @throws IllegalArgumentException si la date fournie est illisible */
    public static Instant parse(String time, Long timestamp, Instant now) {
        if (time != null && !time.isBlank()) {
            try {
                return OffsetDateTime.parse(time.strip()).toInstant();
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException(
                        "Date illisible : attendu au format ISO 8601, par exemple 2026-10-04T10:00:00.000Z.", e);
            }
        }
        if (timestamp != null) {
            if (timestamp <= 0) {
                throw new IllegalArgumentException("Horodatage invalide.");
            }
            return timestamp > MILLIS_THRESHOLD ? Instant.ofEpochMilli(timestamp) : Instant.ofEpochSecond(timestamp);
        }
        return now;
    }
}
