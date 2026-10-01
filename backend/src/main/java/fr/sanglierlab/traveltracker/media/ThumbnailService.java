package fr.sanglierlab.traveltracker.media;

import fr.sanglierlab.traveltracker.common.ApiException;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;

/**
 * Génère l'image « pleine page » (~1920 px) et la miniature (~400 px) en JPEG, avec Thumbnailator.
 *
 * <ul>
 *   <li>L'orientation EXIF est appliquée avant le redimensionnement (Thumbnailator le fait à la lecture).</li>
 *   <li>Les JPEG produits ne contiennent aucune métadonnée : le GPS de la photo n'est donc jamais exposé.
 *       Seul le fichier original, téléchargeable, le conserve.</li>
 *   <li>Une image n'est jamais agrandie.</li>
 *   <li>La photo n'est décodée qu'UNE fois : la miniature est calculée à partir de l'image pleine page,
 *       déjà réduite (plus rapide et moins gourmand en mémoire sur le NAS).</li>
 *   <li>Les dimensions sont contrôlées AVANT le décodage (protection mémoire).</li>
 *   <li>Un seul traitement à la fois ({@code synchronized}).</li>
 * </ul>
 */
@Service
public class ThumbnailService {

    public static final int THUMB_SIZE = 400;
    public static final int DISPLAY_SIZE = 1920;

    private static final double QUALITY = 0.85;
    private static final long MAX_PIXELS = 100_000_000L;

    /** Dimensions de l'image produite ({@code null} quand il n'y en a pas). */
    public record Dimensions(Integer width, Integer height) {
        public static final Dimensions NONE = new Dimensions(null, null);
    }

    /** Photo : produit l'image pleine page et la miniature. Renvoie les dimensions de l'image pleine page. */
    public synchronized Dimensions generateDisplayAndThumb(Path source, Path displayTarget, Path thumbTarget)
            throws IOException {
        BufferedImage display = Thumbnails.of(source.toFile())
                .scale(scaleToFit(readDimensions(source), DISPLAY_SIZE))
                .asBufferedImage();
        writeJpeg(display, displayTarget);
        writeThumb(display, thumbTarget);
        return new Dimensions(display.getWidth(), display.getHeight());
    }

    /** Vidéo : produit seulement la miniature, à partir de l'image extraite. Renvoie les dimensions de la miniature. */
    public synchronized Dimensions generateThumb(Path source, Path thumbTarget) throws IOException {
        BufferedImage image = Thumbnails.of(source.toFile())
                .scale(scaleToFit(readDimensions(source), THUMB_SIZE))
                .asBufferedImage();
        writeJpeg(image, thumbTarget);
        return new Dimensions(image.getWidth(), image.getHeight());
    }

    // -----------------------------------------------------------------------

    private void writeThumb(BufferedImage display, Path thumbTarget) throws IOException {
        Thumbnails.of(display)
                .scale(scaleToFit(new int[]{display.getWidth(), display.getHeight()}, THUMB_SIZE))
                .outputFormat("jpg")
                .outputQuality(QUALITY)
                .toFile(thumbTarget.toFile());
    }

    private void writeJpeg(BufferedImage image, Path target) throws IOException {
        Thumbnails.of(image)
                .scale(1.0)
                .outputFormat("jpg")
                .outputQuality(QUALITY)
                .toFile(target.toFile());
    }

    /** Facteur d'échelle pour que le plus grand côté tienne dans maxSide, sans jamais agrandir. */
    static double scaleToFit(int[] dimensions, int maxSide) {
        int longest = Math.max(dimensions[0], dimensions[1]);
        return longest <= maxSide ? 1.0 : (double) maxSide / longest;
    }

    /** Lit largeur et hauteur dans l'en-tête de l'image, sans la décoder, et refuse les images démesurées. */
    static int[] readDimensions(Path file) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(file.toFile())) {
            Iterator<ImageReader> readers = input == null ? null : ImageIO.getImageReaders(input);
            if (readers == null || !readers.hasNext()) {
                throw ApiException.badRequest("Image illisible ou format non pris en charge.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if ((long) width * height > MAX_PIXELS) {
                    throw ApiException.badRequest("Image trop grande (plus de 100 mégapixels).");
                }
                return new int[]{width, height};
            } finally {
                reader.dispose();
            }
        }
    }
}
