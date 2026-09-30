package fr.sanglierlab.travel.gallery.dto;

/**
 * Position déduite des coordonnées EXIF des photos d'une galerie.
 *
 * Proposée à l'administrateur, jamais appliquée d'office : une galerie
 * représente un lieu choisi, pas le barycentre de ses clichés.
 */
public record PositionSuggestionDto(
        boolean available,
        Double latitude,
        Double longitude,
        long basedOnPhotos
) {
    public static PositionSuggestionDto none() {
        return new PositionSuggestionDto(false, null, null, 0);
    }
}
