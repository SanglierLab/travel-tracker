package fr.sanglierlab.travel.gallery;

import fr.sanglierlab.travel.common.PageDto;
import fr.sanglierlab.travel.gallery.dto.GalleryDetailDto;
import fr.sanglierlab.travel.gallery.dto.GalleryMarkerDto;
import fr.sanglierlab.travel.gallery.dto.GallerySummaryDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Consultation publique. Aucune authentification : le partage familial est
 * l'objet même du site.
 */
@RestController
@RequestMapping("/api/galleries")
public class GalleryController {

    private final GalleryService service;

    public GalleryController(GalleryService service) {
        this.service = service;
    }

    /** Timeline anti-chronologique, paginée. */
    @GetMapping
    public PageDto<GallerySummaryDto> list(@RequestParam(required = false) Integer page,
                                           @RequestParam(required = false) Integer size) {
        return service.listPublished(page, size);
    }

    /** Marqueurs de la carte, dans l'ordre chronologique du voyage. */
    @GetMapping("/markers")
    public List<GalleryMarkerDto> markers() {
        return service.markers();
    }

    @GetMapping("/{id}")
    public GalleryDetailDto get(@PathVariable Long id) {
        return service.getPublished(id);
    }
}
