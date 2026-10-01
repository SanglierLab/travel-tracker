package fr.sanglierlab.traveltracker.media;

import fr.sanglierlab.traveltracker.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Accès au système de fichiers pour les médias.
 *
 * <pre>
 * {media-dir}/{galerie}/{uuid}-thumb.jpg      miniature (~400 px), photos et vidéos
 * {media-dir}/{galerie}/{uuid}-display.jpg    pleine page (~1920 px), photos
 * {media-dir}/{galerie}/{uuid}-original.{ext} fichier d'origine (photo, ou vidéo MP4)
 * </pre>
 *
 * Les noms ne contiennent jamais rien qui vienne de l'utilisateur : identifiant numérique de galerie,
 * UUID généré par le serveur, extension issue du format détecté.
 */
@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private final Path mediaRoot;

    @Autowired
    public StorageService(AppProperties properties) {
        this(properties.mediaDir());
    }

    StorageService(Path mediaRoot) {
        this.mediaRoot = mediaRoot.toAbsolutePath().normalize();
        log.info("Dossier des médias : {}", this.mediaRoot);
    }

    public Path getMediaRoot() {
        return mediaRoot;
    }

    public Path galleryDir(long galleryId) {
        return resolve(Long.toString(galleryId));
    }

    public void prepareGalleryDir(long galleryId) throws IOException {
        Files.createDirectories(galleryDir(galleryId));
    }

    public Path originalPath(long galleryId, String fileKey, String extension) {
        return resolve(galleryId + "/" + checkedKey(fileKey) + "-original." + sanitizeExtension(extension));
    }

    public Path displayPath(long galleryId, String fileKey) {
        return resolve(galleryId + "/" + checkedKey(fileKey) + "-display.jpg");
    }

    public Path thumbPath(long galleryId, String fileKey) {
        return resolve(galleryId + "/" + checkedKey(fileKey) + "-thumb.jpg");
    }

    /** Supprime les (jusqu'à) trois fichiers d'un média. Ne lève jamais d'exception : les échecs sont journalisés. */
    public void deleteMedia(long galleryId, String fileKey, String extension) {
        deleteQuietly(thumbPath(galleryId, fileKey));
        deleteQuietly(displayPath(galleryId, fileKey));
        deleteQuietly(originalPath(galleryId, fileKey, extension));
    }

    /** Supprime le dossier d'une galerie et tout son contenu. Ne lève jamais d'exception. */
    public void deleteGalleryDir(long galleryId) {
        Path dir = galleryDir(galleryId);
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(this::deleteQuietly);
        } catch (IOException e) {
            log.warn("Impossible de supprimer le dossier {} : {}", dir, e.getMessage());
        }
    }

    // -----------------------------------------------------------------------

    private Path resolve(String relativePath) {
        Path resolved = mediaRoot.resolve(relativePath).normalize();
        if (!resolved.startsWith(mediaRoot)) {
            throw new IllegalArgumentException("Chemin hors du dossier des médias : " + relativePath);
        }
        return resolved;
    }

    /** La clé doit être un UUID : cela exclut tout caractère de chemin. */
    private static String checkedKey(String fileKey) {
        return UUID.fromString(fileKey).toString();
    }

    private static String sanitizeExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return "bin";
        }
        String cleaned = extension.toLowerCase().replaceAll("[^a-z0-9]", "");
        return cleaned.isEmpty() ? "bin" : cleaned.substring(0, Math.min(5, cleaned.length()));
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Impossible de supprimer {} : {}", path, e.getMessage());
        }
    }
}
