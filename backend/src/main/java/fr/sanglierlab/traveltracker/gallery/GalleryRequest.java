package fr.sanglierlab.traveltracker.gallery;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Création et modification d'une galerie. */
public record GalleryRequest(
        @NotBlank(message = "Le titre est obligatoire.")
        @Size(max = 200, message = "Le titre est limité à 200 caractères.")
        String title,

        @NotBlank(message = "Le nom du lieu est obligatoire.")
        @Size(max = 200, message = "Le nom du lieu est limité à 200 caractères.")
        String placeName,

        @NotNull(message = "La date est obligatoire.")
        LocalDate galleryDate,

        @NotNull(message = "La latitude est obligatoire.")
        @DecimalMin(value = "-90", message = "La latitude doit être comprise entre -90 et 90.")
        @DecimalMax(value = "90", message = "La latitude doit être comprise entre -90 et 90.")
        BigDecimal latitude,

        @NotNull(message = "La longitude est obligatoire.")
        @DecimalMin(value = "-180", message = "La longitude doit être comprise entre -180 et 180.")
        @DecimalMax(value = "180", message = "La longitude doit être comprise entre -180 et 180.")
        BigDecimal longitude) {
}
