package fr.sanglierlab.travel.element.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Nouvel ordre du flux : la liste complète des identifiants, dans l'ordre voulu.
 *
 * Envoyer l'ordre entier plutôt qu'un déplacement élémentaire évite toute
 * ambiguïté quand plusieurs réorganisations s'enchaînent sur un réseau lent.
 */
public record ReorderForm(

        @NotEmpty(message = "La liste des éléments est obligatoire")
        List<Long> elementIds
) {}
