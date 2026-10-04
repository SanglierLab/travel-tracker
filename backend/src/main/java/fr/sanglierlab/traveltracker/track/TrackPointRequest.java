package fr.sanglierlab.traveltracker.track;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Corps envoyé par GPSLogger : {@code {"latitude":%LAT,"longitude":%LON,"time":"%TIME"}}.
 * Les champs inconnus sont ignorés. Si {@code time} manque ou vaut « », {@code timestamp} (%TIMESTAMP) est utilisé,
 * sinon l'heure de réception.
 */
public record TrackPointRequest(
        @NotNull(message = "La latitude est obligatoire.")
        @DecimalMin(value = "-90", message = "La latitude doit être comprise entre -90 et 90.")
        @DecimalMax(value = "90", message = "La latitude doit être comprise entre -90 et 90.")
        BigDecimal latitude,

        @NotNull(message = "La longitude est obligatoire.")
        @DecimalMin(value = "-180", message = "La longitude doit être comprise entre -180 et 180.")
        @DecimalMax(value = "180", message = "La longitude doit être comprise entre -180 et 180.")
        BigDecimal longitude,

        String time,

        Long timestamp) {
}
