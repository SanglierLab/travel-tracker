package fr.sanglierlab.travel.gallery.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Création et modification d'une galerie.
 *
 * Aucune contrainte sur l'alphabet : les noms propres japonais, grecs ou
 * cyrilliques doivent passer tels quels. La base est en utf8mb4.
 */
public record GalleryForm(

        @NotNull(message = "La date est obligatoire")
        LocalDate date,

        @NotBlank(message = "Le lieu est obligatoire")
        @Size(max = 160, message = "Le lieu ne peut dépasser 160 caractères")
        String place,

        @NotBlank(message = "Le titre est obligatoire")
        @Size(max = 200, message = "Le titre ne peut dépasser 200 caractères")
        String title,

        @NotNull(message = "La latitude est obligatoire")
        @DecimalMin(value = "-90", message = "Latitude hors limites")
        @DecimalMax(value = "90", message = "Latitude hors limites")
        Double latitude,

        @NotNull(message = "La longitude est obligatoire")
        @DecimalMin(value = "-180", message = "Longitude hors limites")
        @DecimalMax(value = "180", message = "Longitude hors limites")
        Double longitude,

        /** Absent vaut publié : le cas courant. */
        Boolean published
) {
    public boolean publishedOrDefault() {
        return published == null || published;
    }
}
