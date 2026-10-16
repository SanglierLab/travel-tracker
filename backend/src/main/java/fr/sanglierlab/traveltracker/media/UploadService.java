package fr.sanglierlab.traveltracker.media;

import fr.sanglierlab.traveltracker.common.ApiException;
import fr.sanglierlab.traveltracker.config.AppProperties;
import fr.sanglierlab.traveltracker.gallery.GalleryRepository;
import fr.sanglierlab.traveltracker.gallery.ItemDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Envoi d'un fichier <b>par morceaux, avec reprise</b>, pour les connexions instables (4G / 5G, wifi d'hôtel).
 *
 * <ol>
 *   <li>{@link #start} : le client annonce nom, taille et empreinte ; le serveur crée un envoi (ou retrouve l'envoi interrompu du même fichier).</li>
 *   <li>{@link #append} : morceaux envoyés dans l'ordre, chacun à l'octet où le serveur en est. Un morceau coupé en route est retiré : le fichier
 *       ne contient jamais de morceau partiel, le client réessaie le même morceau sans risque.</li>
 *   <li>{@link #complete} : une fois tout reçu, le fichier passe par le traitement habituel (miniatures, EXIF, base). <b>Idempotent</b> : si la
 *       réponse se perd et que le client redemande, il reçoit le même élément, jamais un doublon.</li>
 * </ol>
 *
 * <p>État sur disque, dans {@code <médias>/.uploads} (même disque que les médias : le fichier terminé est déplacé, pas copié) :
 * {@code <id>.part} (octets reçus), {@code <id>.meta} (galerie, nom, taille, empreinte), {@code <id>.done} (identifiant de l'élément créé).
 * Il survit à un redémarrage du serveur. Les envois abandonnés depuis plus de 48 h sont effacés au début d'un nouvel envoi.
 *
 * <p>Dès le premier morceau, le type du fichier est reconnu sur son contenu et sa taille comparée à la limite de son type : un fichier
 * refusé l'est avant d'en avoir envoyé la suite.
 */
@Service
public class UploadService {

    private static final Logger log = LoggerFactory.getLogger(UploadService.class);

    public static final int CHUNK_BYTES = 2 * 1024 * 1024;
    public static final int MAX_CHUNK_BYTES = 8 * 1024 * 1024;
    static final Duration EXPIRY = Duration.ofHours(48);

    private final Path dir;
    private final GalleryRepository galleries;
    private final MediaService media;
    private final AppProperties properties;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    @Autowired
    public UploadService(StorageService storage, GalleryRepository galleries, MediaService media, AppProperties properties) {
        this(storage.getMediaRoot().resolve(".uploads"), galleries, media, properties);
    }

    UploadService(Path dir, GalleryRepository galleries, MediaService media, AppProperties properties) {
        this.dir = dir;
        this.galleries = galleries;
        this.media = media;
        this.properties = properties;
    }

    // -----------------------------------------------------------------------

    public UploadStatus start(long galleryId, UploadRequest request) throws IOException {
        if (!galleries.existsById(galleryId)) {
            throw ApiException.notFound("Galerie");
        }
        long biggest = Math.max(properties.maxVideoSize().toBytes(), properties.maxPhotoSize().toBytes());
        if (request.size() > biggest) {
            throw ApiException.payloadTooLarge("Fichier trop volumineux (maximum " + biggest / (1024 * 1024) + " Mo).");
        }
        Files.createDirectories(dir);
        purgeExpired();

        String fingerprint = hash(galleryId + "|" + request.size() + "|" + (request.fingerprint() == null ? "" : request.fingerprint()));
        if (request.fingerprint() != null && !request.fingerprint().isBlank()) {
            try (Stream<Path> metas = Files.list(dir)) {
                for (Path meta : (Iterable<Path>) metas.filter(p -> p.getFileName().toString().endsWith(".meta"))::iterator) {
                    String id = baseName(meta);
                    synchronized (lock(id)) {
                        Properties known = readMeta(id);
                        if (known != null && fingerprint.equals(known.getProperty("fingerprint")) && Files.exists(part(id))) {
                            log.info("Envoi repris : {} octets déjà reçus sur {}", Files.size(part(id)), request.size());
                            return status(id, known);
                        }
                    }
                }
            }
        }

        String id = UUID.randomUUID().toString();
        Properties meta = new Properties();
        meta.setProperty("galleryId", Long.toString(galleryId));
        meta.setProperty("filename", request.filename());
        meta.setProperty("size", Long.toString(request.size()));
        meta.setProperty("fingerprint", fingerprint);
        synchronized (lock(id)) {
            Files.createFile(part(id));
            writeMeta(id, meta);
        }
        return new UploadStatus(id, 0, request.size(), CHUNK_BYTES);
    }

    /** Où en est l'envoi (le client s'en sert pour se resynchroniser après une erreur). */
    public UploadStatus status(String id) throws IOException {
        synchronized (lock(id)) {
            Properties meta = requireMeta(id);
            return status(id, meta);
        }
    }

    /**
     * Ajoute un morceau, qui doit commencer exactement à l'octet reçu jusqu'ici. Un morceau interrompu en route est retiré.
     *
     * @param contentLength taille annoncée du morceau, ou -1 si inconnue
     * @throws UploadOffsetException si {@code offset} n'est pas l'octet attendu
     */
    public UploadStatus append(String id, long offset, InputStream body, long contentLength) throws IOException {
        synchronized (lock(id)) {
            Properties meta = requireMeta(id);
            long size = Long.parseLong(meta.getProperty("size"));
            long before = Files.size(part(id));
            if (offset != before) {
                throw new UploadOffsetException(before);
            }
            long room = Math.min(MAX_CHUNK_BYTES, size - before);
            if (room <= 0) {
                throw ApiException.conflict("Tous les octets de ce fichier sont déjà reçus : validez l'envoi.");
            }
            if (contentLength > room) {
                throw ApiException.payloadTooLarge("Morceau trop gros (au plus " + room + " octets attendus ici).");
            }

            long written;
            try (FileChannel channel = FileChannel.open(part(id), StandardOpenOption.WRITE)) {
                channel.position(before);
                written = copy(body, channel, room);
                if (written > room || (contentLength >= 0 && written != contentLength)) {
                    channel.truncate(before);
                    throw written > room
                            ? ApiException.payloadTooLarge("Morceau trop gros.")
                            : new ApiException(HttpStatus.REQUEST_TIMEOUT, "Morceau incomplet : réessayez.");
                }
            } catch (IOException e) {
                truncateQuietly(id, before); // connexion coupée en plein morceau : on ne garde rien de ce morceau
                throw e;
            }

            if (before < FileTypeDetector.HEADER_SIZE) {
                checkStart(id, size);
            }
            touch(meta(id)); // l'envoi est vivant : il ne doit pas être nettoyé comme abandonné
            return status(id, meta);
        }
    }

    /** Valide l'envoi : traitement habituel du fichier complet. Redemandée après une réponse perdue, renvoie le même élément. */
    public ItemDto complete(String id) throws IOException {
        synchronized (lock(id)) {
            Path done = done(id);
            if (Files.exists(done)) {
                return media.existing(Long.parseLong(Files.readString(done, StandardCharsets.UTF_8).strip()));
            }
            Properties meta = requireMeta(id);
            long size = Long.parseLong(meta.getProperty("size"));
            long received = Files.size(part(id));
            if (received != size) {
                throw new UploadOffsetException(received);
            }
            try {
                ItemDto item = media.ingest(Long.parseLong(meta.getProperty("galleryId")), part(id), meta.getProperty("filename"));
                Files.writeString(done, Long.toString(item.id()), StandardCharsets.UTF_8);
                Files.deleteIfExists(meta(id));
                return item;
            } catch (ApiException e) {
                drop(id); // fichier refusé : inutile de le garder
                throw e;
            } catch (IOException | RuntimeException e) {
                if (!Files.exists(part(id))) {
                    drop(id); // le fichier est déjà parti (échec après déplacement) : l'envoi ne peut plus reprendre
                }
                throw e;
            }
        }
    }

    /** Abandonne un envoi (le client annule) : efface ce qui a été reçu. */
    public void cancel(String id) {
        synchronized (lock(id)) {
            drop(id);
        }
    }

    // -----------------------------------------------------------------------

    /** Type reconnu sur le contenu, et taille comparée à la limite de ce type, dès que l'en-tête du fichier est reçu. */
    private void checkStart(String id, long declaredSize) throws IOException {
        byte[] header;
        try (InputStream in = Files.newInputStream(part(id))) {
            header = in.readNBytes(FileTypeDetector.HEADER_SIZE);
        }
        if (header.length < FileTypeDetector.HEADER_SIZE && Files.size(part(id)) < declaredSize) {
            return; // en-tête incomplet : on attend le morceau suivant
        }
        try {
            media.checkDeclaredSize(media.detectHeader(header), declaredSize);
        } catch (ApiException e) {
            drop(id);
            throw e;
        }
    }

    private UploadStatus status(String id, Properties meta) throws IOException {
        return new UploadStatus(id, Files.size(part(id)), Long.parseLong(meta.getProperty("size")), CHUNK_BYTES);
    }

    private static long copy(InputStream in, FileChannel channel, long room) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > room) {
                return total; // trop de données : on s'arrête, l'appelant retire tout
            }
            ByteBuffer out = ByteBuffer.wrap(buffer, 0, read);
            while (out.hasRemaining()) {
                channel.write(out);
            }
        }
        return total;
    }

    private void touch(Path file) {
        try {
            Files.setLastModifiedTime(file, FileTime.from(Instant.now()));
        } catch (IOException e) {
            log.debug("Date de {} non mise à jour : {}", file, e.getMessage());
        }
    }

    private void truncateQuietly(String id, long length) {
        try (FileChannel channel = FileChannel.open(part(id), StandardOpenOption.WRITE)) {
            channel.truncate(length);
        } catch (IOException e) {
            log.warn("Impossible de retirer un morceau incomplet de l'envoi {} : {}", id, e.getMessage());
        }
    }

    private void drop(String id) {
        deleteQuietly(part(id));
        deleteQuietly(meta(id));
        locks.remove(id);
    }

    /** Efface les envois (et marques « terminé ») inactifs depuis plus de 48 h. */
    void purgeExpired() {
        Instant limit = Instant.now().minus(EXPIRY);
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> {
                String name = p.getFileName().toString();
                return name.endsWith(".part") || name.endsWith(".meta") || name.endsWith(".done");
            }).forEach(p -> {
                try {
                    if (Files.getLastModifiedTime(p).toInstant().isBefore(limit)) {
                        locks.remove(baseName(p));
                        Files.deleteIfExists(p);
                    }
                } catch (IOException e) {
                    log.warn("Impossible de nettoyer {} : {}", p, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.warn("Impossible de parcourir {} : {}", dir, e.getMessage());
        }
    }

    // --- fichiers d'un envoi ---

    private Properties requireMeta(String id) throws IOException {
        Properties meta = readMeta(id);
        if (meta == null || !Files.exists(part(id))) {
            throw ApiException.notFound("Envoi");
        }
        return meta;
    }

    private Properties readMeta(String id) throws IOException {
        Path file = meta(id);
        if (!Files.exists(file)) {
            return null;
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            properties.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }

    private void writeMeta(String id, Properties meta) throws IOException {
        try (OutputStream out = Files.newOutputStream(meta(id))) {
            meta.store(new java.io.OutputStreamWriter(out, StandardCharsets.UTF_8), null);
        }
    }

    /** L'identifiant doit être un UUID : cela exclut tout caractère de chemin. */
    private Path file(String id, String extension) {
        try {
            return dir.resolve(UUID.fromString(id) + "." + extension);
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound("Envoi");
        }
    }

    private Path part(String id) {
        return file(id, "part");
    }

    private Path meta(String id) {
        return file(id, "meta");
    }

    private Path done(String id) {
        return file(id, "done");
    }

    private static String baseName(Path path) {
        String name = path.getFileName().toString();
        return name.substring(0, name.lastIndexOf('.'));
    }

    private Object lock(String id) {
        // Valide l'identifiant avant de créer un verrou : pas de verrou pour des valeurs inventées.
        part(id);
        return locks.computeIfAbsent(id, key -> new Object());
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Impossible de supprimer {} : {}", path, e.getMessage());
        }
    }

    private static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
