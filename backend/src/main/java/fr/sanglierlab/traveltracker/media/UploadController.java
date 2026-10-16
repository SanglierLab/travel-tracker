package fr.sanglierlab.traveltracker.media;

import fr.sanglierlab.traveltracker.common.ApiError;
import fr.sanglierlab.traveltracker.gallery.ItemDto;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Envoi d'un média par morceaux avec reprise (voir {@link UploadService}). Les envois d'un seul tenant par
 * {@code POST /api/admin/galleries/{id}/items/media} (curl, scripts) restent disponibles.
 */
@RestController
@RequestMapping("/api/admin")
public class UploadController {

    private static final Logger log = LoggerFactory.getLogger(UploadController.class);

    private final UploadService uploads;

    public UploadController(UploadService uploads) {
        this.uploads = uploads;
    }

    /** Début (ou reprise) d'un envoi. */
    @PostMapping("/galleries/{galleryId}/uploads")
    public UploadStatus start(@PathVariable long galleryId, @Valid @RequestBody UploadRequest request) throws IOException {
        return uploads.start(galleryId, request);
    }

    @GetMapping("/uploads/{id}")
    public UploadStatus status(@PathVariable String id) throws IOException {
        return uploads.status(id);
    }

    /** Un morceau : octets bruts dans le corps, {@code offset} = octets déjà reçus. 409 avec {@code received} si le décalage est faux. */
    @PutMapping("/uploads/{id}")
    public ResponseEntity<?> chunk(@PathVariable String id, @RequestParam long offset, InputStream body,
                                   @RequestHeader(value = "Content-Length", defaultValue = "-1") long contentLength) {
        try {
            return ResponseEntity.ok(uploads.append(id, offset, body, contentLength));
        } catch (IOException e) {
            // Connexion coupée en plein morceau : fréquent en mobile, le client réessaie. Pas de trace d'erreur.
            log.debug("Morceau interrompu pour l'envoi {} : {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(new ApiError("Morceau interrompu : réessayez."));
        }
    }

    /** Valide l'envoi. Idempotent : redemandé après une réponse perdue, renvoie le même élément. */
    @PostMapping("/uploads/{id}/complete")
    @ResponseStatus(HttpStatus.CREATED)
    public ItemDto complete(@PathVariable String id) throws IOException {
        return uploads.complete(id);
    }

    @DeleteMapping("/uploads/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable String id) {
        uploads.cancel(id);
    }

    @ExceptionHandler(UploadOffsetException.class)
    ResponseEntity<Map<String, Object>> offset(UploadOffsetException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", "Reprise nécessaire : le serveur a reçu " + e.received() + " octets.", "received", e.received()));
    }
}
