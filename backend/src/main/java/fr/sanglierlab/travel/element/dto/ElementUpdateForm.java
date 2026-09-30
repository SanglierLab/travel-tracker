package fr.sanglierlab.travel.element.dto;

import jakarta.validation.constraints.Size;

/**
 * Modification d'un élément existant : la légende pour un média, le contenu
 * pour un bloc de texte. Seul le champ pertinent est pris en compte.
 */
public record ElementUpdateForm(

        @Size(max = 255, message = "La légende ne peut dépasser 255 caractères")
        String caption,

        @Size(max = 20000, message = "Le texte ne peut dépasser 20 000 caractères")
        String markdown
) {}
