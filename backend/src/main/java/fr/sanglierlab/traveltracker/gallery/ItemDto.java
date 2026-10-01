package fr.sanglierlab.traveltracker.gallery;

/**
 * Élément de galerie tel que vu par le front.
 * <ul>
 *   <li>TEXT : textMarkdown (pour l'édition) et html (déjà assaini, prêt à afficher).</li>
 *   <li>PHOTO : thumbUrl, displayUrl (plein écran), originalUrl (téléchargement).</li>
 *   <li>VIDEO : thumbUrl (null si pas de miniature), originalUrl (lecture et téléchargement).</li>
 * </ul>
 * width / height : à utiliser pour réserver la place avant le chargement de l'image.
 */
public record ItemDto(
        long id,
        ItemType type,
        int sortOrder,
        String textMarkdown,
        String html,
        String thumbUrl,
        String displayUrl,
        String originalUrl,
        String originalFilename,
        Integer width,
        Integer height,
        Long sizeBytes) {
}
