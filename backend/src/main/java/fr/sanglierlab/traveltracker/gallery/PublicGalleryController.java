package fr.sanglierlab.traveltracker.gallery;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Partie publique : lecture seule, sans authentification. */
@RestController
@RequestMapping("/api/public")
public class PublicGalleryController {

    private final GalleryService galleries;

    public PublicGalleryController(GalleryService galleries) {
        this.galleries = galleries;
    }

    /** Une page de galeries avec leur contenu (page numérotée à partir de 1). */
    @GetMapping("/galleries")
    public PageResponse<GalleryDetail> page(@RequestParam(defaultValue = "1") int page) {
        return galleries.publicPage(page);
    }

    /** Toutes les galeries (sans contenu), dans l'ordre d'affichage : la carte les affiche toutes. */
    @GetMapping("/map")
    public List<GallerySummary> map() {
        return galleries.summaries();
    }
}
