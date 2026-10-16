package fr.sanglierlab.traveltracker.media;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Début d'un envoi par morceaux.
 *
 * @param filename    nom d'origine du fichier (proposé au téléchargement)
 * @param size        taille totale en octets
 * @param fingerprint empreinte choisie par le client (nom, taille, date de modification) : si le même fichier est proposé de nouveau
 *                    après une interruption (même après un rechargement de la page), l'envoi reprend où il s'était arrêté
 */
public record UploadRequest(
        @NotBlank(message = "Le nom du fichier est obligatoire.")
        @Size(max = 400, message = "Le nom du fichier est trop long.")
        String filename,
        @Min(value = 1, message = "Le fichier est vide.")
        long size,
        @Size(max = 600, message = "L'empreinte est trop longue.")
        String fingerprint) {
}
