package fr.sanglierlab.traveltracker.gallery;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Bloc de texte en markdown. */
public record TextRequest(
        @NotBlank(message = "Le texte ne peut pas être vide.")
        @Size(max = 20000, message = "Le texte est limité à 20 000 caractères.")
        String markdown) {
}
