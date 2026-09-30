package fr.sanglierlab.travel.element.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Bloc de récit, en Markdown.
 *
 * {@code afterElementId} indique après quel élément l'insérer ; absent, le bloc
 * est ajouté à la fin.
 */
public record TextBlockForm(

        @NotBlank(message = "Le texte ne peut pas être vide")
        @Size(max = 20000, message = "Le texte ne peut dépasser 20 000 caractères")
        String markdown,

        Long afterElementId
) {}
