package fr.sanglierlab.travel.element;

import fr.sanglierlab.travel.element.dto.ElementDto;
import fr.sanglierlab.travel.element.dto.ElementUpdateForm;
import fr.sanglierlab.travel.element.dto.ReorderForm;
import fr.sanglierlab.travel.element.dto.TextBlockForm;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Contenu des galeries : médias, récits, ordre.
 */
@RestController
@RequestMapping("/api/admin")
public class ElementAdminController {

    private final ElementService service;

    public ElementAdminController(ElementService service) {
        this.service = service;
    }

    /**
     * Envoi de médias. Plusieurs fichiers par requête ; ceux qui échouent sont
     * ignorés, les autres sont enregistrés.
     */
    @PostMapping(path = "/galleries/{galleryId}/media", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ElementDto> addMedia(@PathVariable Long galleryId,
                                     @RequestPart("files") List<MultipartFile> files) {
        return service.addMedia(galleryId, files);
    }

    /** Insertion d'un récit, après un élément donné ou en fin de galerie. */
    @PostMapping("/galleries/{galleryId}/text")
    @ResponseStatus(HttpStatus.CREATED)
    public ElementDto addText(@PathVariable Long galleryId,
                              @Valid @RequestBody TextBlockForm form) {
        return service.addTextBlock(galleryId, form);
    }

    /** Nouvel ordre complet du flux. */
    @PutMapping("/galleries/{galleryId}/order")
    public List<ElementDto> reorder(@PathVariable Long galleryId,
                                    @Valid @RequestBody ReorderForm form) {
        return service.reorder(galleryId, form);
    }

    /** Légende d'un média, ou contenu d'un bloc de texte. */
    @PutMapping("/elements/{id}")
    public ElementDto update(@PathVariable Long id, @Valid @RequestBody ElementUpdateForm form) {
        return service.update(id, form);
    }

    @DeleteMapping("/elements/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
