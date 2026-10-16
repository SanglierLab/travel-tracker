package fr.sanglierlab.traveltracker.media;

/**
 * État d'un envoi par morceaux.
 *
 * @param id        identifiant de l'envoi (à rappeler à chaque morceau)
 * @param received  nombre d'octets déjà reçus : le prochain morceau doit commencer exactement là
 * @param size      taille totale annoncée du fichier
 * @param chunkSize taille de morceau conseillée (le client peut en envoyer de plus petits : jusqu'à {@link UploadService#MAX_CHUNK_BYTES})
 */
public record UploadStatus(String id, long received, long size, int chunkSize) {
}
