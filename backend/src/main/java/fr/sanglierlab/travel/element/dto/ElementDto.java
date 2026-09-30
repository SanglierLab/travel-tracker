package fr.sanglierlab.travel.element.dto;

import fr.sanglierlab.travel.element.Element;
import fr.sanglierlab.travel.element.ElementType;

import java.time.Instant;

/**
 * Élément tel que l'API l'expose.
 *
 * Les chemins de stockage deviennent des URL exploitables directement par le
 * navigateur. Le frontend n'a pas à connaître l'organisation du disque.
 */
public record ElementDto(
        Long id,
        int position,
        ElementType type,

        // TEXT
        String markdown,

        // PHOTO / VIDEO
        String thumbUrl,
        String mediumUrl,
        String originalUrl,
        String downloadUrl,
        String caption,
        Integer width,
        Integer height,
        Integer durationSeconds,
        Instant takenAt,
        Long fileSize
) {

    public static ElementDto from(Element element) {
        if (element.getType() == ElementType.TEXT) {
            return new ElementDto(
                    element.getId(), element.getPosition(), ElementType.TEXT,
                    element.getMarkdown(),
                    null, null, null, null, null, null, null, null, null, null);
        }
        return new ElementDto(
                element.getId(),
                element.getPosition(),
                element.getType(),
                null,
                url("thumb", element.getThumbPath()),
                url("medium", element.getMediumPath()),
                url("original", element.getStoragePath()),
                downloadUrl(element.getStoragePath()),
                element.getCaption(),
                element.getWidth(),
                element.getHeight(),
                element.getDurationSeconds(),
                element.getTakenAt(),
                element.getFileSize());
    }

    /**
     * Transforme « thumbs/2026/10/abc.jpg » en « /media/thumb/2026/10/abc.jpg ».
     * Le premier segment, propre au stockage, est remplacé par le nom de la
     * taille attendu par le contrôleur.
     */
    private static String url(String size, String storedPath) {
        if (storedPath == null) {
            return null;
        }
        int slash = storedPath.indexOf('/');
        return "/media/" + size + "/" + (slash < 0 ? storedPath : storedPath.substring(slash + 1));
    }

    private static String downloadUrl(String storedPath) {
        String base = url("original", storedPath);
        return base == null ? null : base + "?download=1";
    }
}
