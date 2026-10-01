package fr.sanglierlab.traveltracker.media;

import fr.sanglierlab.traveltracker.common.ApiException;
import fr.sanglierlab.traveltracker.config.AppProperties;
import fr.sanglierlab.traveltracker.gallery.GalleryItem;
import fr.sanglierlab.traveltracker.gallery.GalleryItemService;
import fr.sanglierlab.traveltracker.gallery.GalleryRepository;
import fr.sanglierlab.traveltracker.gallery.ItemDto;
import fr.sanglierlab.traveltracker.gallery.ItemMapper;
import fr.sanglierlab.traveltracker.gallery.ItemType;
import fr.sanglierlab.traveltracker.media.ThumbnailService.Dimensions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Ajout d'un média (photo ou vidéo) à une galerie.
 *
 * <ol>
 *   <li>Vérifications : galerie existante, fichier non vide, format reconnu sur le CONTENU, taille maximale.</li>
 *   <li>L'original est déplacé sur le disque (aucune copie du fichier en mémoire).</li>
 *   <li>Miniature (et image pleine page pour une photo).</li>
 *   <li>Enregistrement en base, en dernière position.</li>
 * </ol>
 * Cette méthode n'est volontairement PAS transactionnelle : un envoi de plusieurs centaines de Mo ne doit pas
 * garder une connexion à la base ouverte. En cas d'échec, les fichiers déjà écrits sont supprimés.
 */
@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);

    private final GalleryRepository galleries;
    private final GalleryItemService items;
    private final ItemMapper mapper;
    private final StorageService storage;
    private final ThumbnailService thumbnails;
    private final FfmpegService ffmpeg;
    private final AppProperties properties;

    public MediaService(GalleryRepository galleries, GalleryItemService items, ItemMapper mapper,
                        StorageService storage, ThumbnailService thumbnails, FfmpegService ffmpeg,
                        AppProperties properties) {
        this.galleries = galleries;
        this.items = items;
        this.mapper = mapper;
        this.storage = storage;
        this.thumbnails = thumbnails;
        this.ffmpeg = ffmpeg;
        this.properties = properties;
    }

    public ItemDto upload(long galleryId, MultipartFile file) throws IOException {
        if (!galleries.existsById(galleryId)) {
            throw ApiException.notFound("Galerie");
        }
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Le fichier est vide.");
        }

        MediaFormat format = detectFormat(file);
        long maxBytes = (format.type() == ItemType.VIDEO ? properties.maxVideoSize() : properties.maxPhotoSize())
                .toBytes();
        if (file.getSize() > maxBytes) {
            throw ApiException.payloadTooLarge("Fichier trop volumineux (maximum "
                    + maxBytes / (1024 * 1024) + " Mo pour ce type de fichier).");
        }

        String fileKey = UUID.randomUUID().toString();
        try {
            storage.prepareGalleryDir(galleryId);
            Path original = storage.originalPath(galleryId, fileKey, format.extension());
            file.transferTo(original);

            Dimensions dimensions = format.type() == ItemType.VIDEO
                    ? processVideo(galleryId, fileKey, original)
                    : processPhoto(galleryId, fileKey, original, format);

            GalleryItem saved = items.addMedia(galleryId, format.type(), fileKey,
                    cleanFilename(file.getOriginalFilename(), format), format.extension(), file.getSize(),
                    dimensions.width(), dimensions.height());
            log.info("Média ajouté : galerie={}, type={}, fichier={}, {} Ko",
                    galleryId, format.type(), saved.getOriginalFilename(), file.getSize() / 1024);
            return mapper.toDto(saved);
        } catch (IOException | RuntimeException e) {
            storage.deleteMedia(galleryId, fileKey, format.extension());
            throw e;
        }
    }

    // -----------------------------------------------------------------------

    private MediaFormat detectFormat(MultipartFile file) throws IOException {
        try (InputStream in = file.getInputStream()) {
            return FileTypeDetector.detect(in).orElseThrow(() -> ApiException.unsupportedMedia(
                    "Format non accepté. Formats acceptés : JPEG, PNG, WebP (photos) et MP4 (vidéos)."));
        }
    }

    private Dimensions processPhoto(long galleryId, String fileKey, Path original, MediaFormat format)
            throws IOException {
        Path source = original;
        Path converted = null;
        try {
            if (format == MediaFormat.WEBP) {
                converted = Files.createTempFile("webp-", ".jpg");
                ffmpeg.convertToJpeg(original, converted);
                source = converted;
            }
            return thumbnails.generateDisplayAndThumb(source,
                    storage.displayPath(galleryId, fileKey), storage.thumbPath(galleryId, fileKey));
        } finally {
            if (converted != null) {
                Files.deleteIfExists(converted);
            }
        }
    }

    /**
     * Vidéo : la miniature est un « plus » ; si ffmpeg échoue, la vidéo reste utilisable sans miniature
     * (width/height restent null) plutôt que de perdre un envoi de plusieurs centaines de Mo.
     */
    private Dimensions processVideo(long galleryId, String fileKey, Path original) throws IOException {
        Path frame = Files.createTempFile("video-frame-", ".jpg");
        try {
            ffmpeg.extractVideoFrame(original, frame);
            return thumbnails.generateThumb(frame, storage.thumbPath(galleryId, fileKey));
        } catch (IOException | ApiException e) {
            log.warn("Pas de miniature pour la vidéo {} de la galerie {} : {}", fileKey, galleryId, e.getMessage());
            Files.deleteIfExists(storage.thumbPath(galleryId, fileKey));
            return Dimensions.NONE;
        } finally {
            Files.deleteIfExists(frame);
        }
    }

    /** Nom proposé au téléchargement : sans chemin ni caractères de contrôle, 255 caractères maximum. */
    static String cleanFilename(String original, MediaFormat format) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").strip();
        if (name.isEmpty()) {
            name = "media." + format.extension();
        }
        return name.length() > 255 ? name.substring(0, 255) : name;
    }
}
