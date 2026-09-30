package fr.sanglierlab.travel.trip.dto;

import fr.sanglierlab.travel.trip.TripType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Déclaration d'un vol ou d'une traversée.
 *
 * L'identifiant n'est pas validé selon le type : un indicatif d'appel et un
 * MMSI n'ont pas le même format, mais imposer une expression rationnelle
 * stricte reviendrait à bloquer une saisie légitime au moment même où le
 * voyageur en a besoin. Seuls les caractères manifestement inattendus sont
 * écartés.
 */
public record TripForm(

        @NotNull(message = "Le type de trajet est obligatoire")
        TripType type,

        @NotBlank(message = "L'identifiant est obligatoire")
        @Size(max = 32, message = "L'identifiant ne peut dépasser 32 caractères")
        @Pattern(regexp = "[A-Za-z0-9 .\\-]+",
                 message = "L'identifiant ne peut contenir que lettres, chiffres, espaces, points et tirets")
        String identifier,

        @Size(max = 160, message = "Le libellé ne peut dépasser 160 caractères")
        String label,

        @NotNull(message = "L'heure de départ prévue est obligatoire")
        Instant scheduledStart,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Couleur attendue au format #RRGGBB")
        String color
) {}
