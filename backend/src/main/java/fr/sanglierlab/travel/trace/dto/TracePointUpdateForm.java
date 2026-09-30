package fr.sanglierlab.travel.trace.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Correction d'un point mal placé.
 *
 * Un relevé GPS pris dans un tunnel ou entre deux immeubles peut atterrir à
 * plusieurs centaines de mètres du trajet réel. Plutôt que de supprimer le
 * point et de créer un trou, l'administrateur le repositionne.
 */
public record TracePointUpdateForm(

        @NotNull(message = "La latitude est obligatoire")
        @DecimalMin(value = "-90", message = "Latitude hors limites")
        @DecimalMax(value = "90", message = "Latitude hors limites")
        Double latitude,

        @NotNull(message = "La longitude est obligatoire")
        @DecimalMin(value = "-180", message = "Longitude hors limites")
        @DecimalMax(value = "180", message = "Longitude hors limites")
        Double longitude,

        Instant measuredAt
) {}
