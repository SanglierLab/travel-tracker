package fr.sanglierlab.travel.gallery;

import fr.sanglierlab.travel.common.PageDto;
import fr.sanglierlab.travel.gallery.dto.GalleryDetailDto;
import fr.sanglierlab.travel.gallery.dto.GalleryForm;
import fr.sanglierlab.travel.gallery.dto.GallerySummaryDto;
import fr.sanglierlab.travel.gallery.dto.PositionSuggestionDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administration des galeries. Protégé par le rôle ADMIN dans SecurityConfig.
 */
@RestController
@RequestMapping("/api/admin/galleries")
public class GalleryAdminController {

    private final GalleryService service;

    public GalleryAdminController(GalleryService service) {
        this.service = service;
    }

    /** Liste complète, brouillons inclus. */
    @GetMapping
    public PageDto<GallerySummaryDto> list(@RequestParam(required = false) Integer page,
                                           @RequestParam(required = false) Integer size) {
        return service.listAll(page, size);
    }

    @GetMapping("/{id}")
    public GalleryDetailDto get(@PathVariable Long id) {
        return service.getForAdmin(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GalleryDetailDto create(@Valid @RequestBody GalleryForm form) {
        return service.create(form);
    }

    @PutMapping("/{id}")
    public GalleryDetailDto update(@PathVariable Long id, @Valid @RequestBody GalleryForm form) {
        return service.update(id, form);
    }

    /**
     * Suppression définitive : la galerie, ses éléments et ses fichiers.
     * La confirmation est demandée côté interface.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Position déduite des photos déjà envoyées, à proposer sur la carte.
     * Complète le bouton de géolocalisation quand la galerie est renseignée
     * après coup, loin du lieu photographié.
     */
    @GetMapping("/{id}/suggested-position")
    public PositionSuggestionDto suggestedPosition(@PathVariable Long id) {
        return service.suggestPosition(id);
    }
}
