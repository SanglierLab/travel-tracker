package fr.sanglierlab.traveltracker.livetrack;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Enregistrement d'une session LiveTrack : le lien de partage, et un nom facultatif (par exemple « Randonnée du mont Fuji »). */
public record LiveTrackRequest(
        @NotBlank(message = "Le lien LiveTrack est obligatoire.")
        @Size(max = 400, message = "Le lien est trop long.")
        String link,

        @Size(max = 100, message = "Le nom est trop long (100 caractères au maximum).")
        String name) {
}
