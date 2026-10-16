package fr.sanglierlab.traveltracker.stage;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Création / modification d'une étape. Règles qui dépendent du type (position de fin obligatoire pour un trajet,
 * ignorée pour une étape simple) : voir {@link StageService}.
 */
public record StageRequest(
        @NotBlank(message = "Le nom est obligatoire.")
        @Size(max = 200, message = "Le nom est limité à 200 caractères.")
        String name,

        @NotNull(message = "Le type d'étape est obligatoire.")
        StageKind kind,

        @NotNull(message = "La date de début est obligatoire.")
        LocalDate startDate,

        @NotNull(message = "La date de fin est obligatoire.")
        LocalDate endDate,

        @NotNull(message = "La position de début est obligatoire.")
        @DecimalMin(value = "-90", message = "La latitude doit être comprise entre -90 et 90.")
        @DecimalMax(value = "90", message = "La latitude doit être comprise entre -90 et 90.")
        BigDecimal startLatitude,

        @NotNull(message = "La position de début est obligatoire.")
        @DecimalMin(value = "-180", message = "La longitude doit être comprise entre -180 et 180.")
        @DecimalMax(value = "180", message = "La longitude doit être comprise entre -180 et 180.")
        BigDecimal startLongitude,

        @DecimalMin(value = "-90", message = "La latitude doit être comprise entre -90 et 90.")
        @DecimalMax(value = "90", message = "La latitude doit être comprise entre -90 et 90.")
        BigDecimal endLatitude,

        @DecimalMin(value = "-180", message = "La longitude doit être comprise entre -180 et 180.")
        @DecimalMax(value = "180", message = "La longitude doit être comprise entre -180 et 180.")
        BigDecimal endLongitude) {
}
