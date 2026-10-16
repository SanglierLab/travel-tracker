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
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Ajout d'un média (photo ou vidéo) à une galerie.
 *
 * <ol>
 *   <li>Vérifications : galerie existante, fichier non vide, format reconnu sur le CONTENU, taille maximale.</li>
 *   <li>L'original est déplacé sur le disque (aucune copie du fichier en mémoire).</li>
 *   <li>Miniature (et image pleine page pour une photo), puis prises de vue EXIF de la photo.</li>
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
    private final ExifService exif;
    private final AppProperties properties;

    public MediaService(GalleryRepository galleries, GalleryItemService items, ItemMapper mapper,
                        StorageService storage, ThumbnailService thumbnails, FfmpegService ffmpeg,
                        ExifService exif, AppProperties properties) {
        this.galleries = galleries;
        this.items = items;
        this.mapper = mapper;
        this.storage = storage;
        this.thumbnails = thumbnails;
        this.ffmpeg = ffmpeg;
        this.exif = exif;
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
        checkSize(format, file.getSize());

        String fileKey = UUID.randomUUID().toString();
        try {
            storage.prepareGalleryDir(galleryId);
            Path original = storage.originalPath(galleryId, fileKey, format.extension());
            file.transferTo(original);
            return finish(galleryId, format, fileKey, original, cleanFilename(file.getOriginalFilename(), format), file.getSize());
        } catch (IOException | RuntimeException e) {
            storage.deleteMedia(galleryId, fileKey, format.extension());
            throw e;
        }
    }

    /**
     * Ajoute un média dont le fichier est déjà complet sur le disque (envoi par morceaux, voir {@link UploadService}) : mêmes contrôles que
     * {@link #upload}, puis le fichier est <b>déplacé</b> (pas copié) à sa place définitive.
     */
    public ItemDto ingest(long galleryId, Path source, String originalFilename) throws IOException {
        if (!galleries.existsById(galleryId)) {
            throw ApiException.notFound("Galerie");
        }
        long size = Files.size(source);
        if (size == 0) {
            throw ApiException.badRequest("Le fichier est vide.");
        }
        MediaFormat format;
        try (InputStream in = Files.newInputStream(source)) {
            format = FileTypeDetector.detect(in).orElseThrow(() -> ApiException.unsupportedMedia(
                    "Format non accepté. Formats acceptés : JPEG, PNG, WebP (photos) et MP4 (vidéos)."));
        }
        checkSize(format, size);

        String fileKey = UUID.randomUUID().toString();
        try {
            storage.prepareGalleryDir(galleryId);
            Path original = storage.originalPath(galleryId, fileKey, format.extension());
            Files.move(source, original, StandardCopyOption.REPLACE_EXISTING);
            return finish(galleryId, format, fileKey, original, cleanFilename(originalFilename, format.extension()), size);
        } catch (IOException | RuntimeException e) {
            storage.deleteMedia(galleryId, fileKey, format.extension());
            throw e;
        }
    }

    /** Refuse un fichier plus gros que la limite de son type (photo ou vidéo). */
    void checkSize(MediaFormat format, long size) {
        long maxBytes = (format.type() == ItemType.VIDEO ? properties.maxVideoSize() : properties.maxPhotoSize()).toBytes();
        if (size > maxBytes) {
            throw ApiException.payloadTooLarge("Fichier trop volumineux (maximum " + maxBytes / (1024 * 1024) + " Mo pour ce type de fichier).");
        }
    }

    /** Début d'un fichier (au moins {@link FileTypeDetector#HEADER_SIZE} octets) : son format, ou une erreur 415 s'il n'est pas accepté. */
    public MediaFormat detectHeader(byte[] header) {
        return FileTypeDetector.detect(header).orElseThrow(() -> ApiException.unsupportedMedia(
                "Format non accepté. Formats acceptés : JPEG, PNG, WebP (photos) et MP4 (vidéos)."));
    }

    /** Contrôle de taille d'un fichier d'après son début, avant d'en recevoir la suite (envoi par morceaux). */
    public void checkDeclaredSize(MediaFormat format, long declaredSize) {
        checkSize(format, declaredSize);
    }

    /** Un élément déjà enregistré, tel que le voit le front (idempotence de la validation d'un envoi). */
    public ItemDto existing(long itemId) {
        return mapper.toDto(items.get(itemId));
    }

    /** Miniatures, EXIF et enregistrement en base d'un original déjà à sa place. */
    private ItemDto finish(long galleryId, MediaFormat format, String fileKey, Path original, String filename, long size)
            throws IOException {
        Dimensions dimensions = format.type() == ItemType.VIDEO
                ? processVideo(galleryId, fileKey, original)
                : processPhoto(galleryId, fileKey, original, format);
        // Prises de vue : lues dans l'original (l'image pleine page et la miniature n'ont plus de métadonnées). Photos seulement.
        String exifJson = format.type() == ItemType.VIDEO ? null : exif.extractJson(original).orElse(null);

        GalleryItem saved = items.addMedia(galleryId, format.type(), fileKey, filename, format.extension(), size,
                dimensions.width(), dimensions.height(), exifJson);
        log.info("Média ajouté : galerie={}, type={}, fichier={}, {} Ko",
                galleryId, format.type(), saved.getOriginalFilename(), size / 1024);
        return mapper.toDto(saved);
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
        return cleanFilename(original, format.extension());
    }

    static String cleanFilename(String original, String fallbackExtension) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").strip();
        if (name.isEmpty()) {
            name = "media." + fallbackExtension;
        }
        return name.length() > 255 ? name.substring(0, 255) : name;
    }
}
