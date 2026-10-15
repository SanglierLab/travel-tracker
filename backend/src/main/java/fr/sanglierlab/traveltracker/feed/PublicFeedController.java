package fr.sanglierlab.traveltracker.feed;

import fr.sanglierlab.traveltracker.gallery.GallerySummary;
import fr.sanglierlab.traveltracker.gallery.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Partie publique : lecture seule, sans authentification. */
@RestController
@RequestMapping("/api/public")
public class PublicFeedController {

    private final FeedService feed;

    public PublicFeedController(FeedService feed) {
        this.feed = feed;
    }

    /** Une page du fil : albums (avec leur contenu) et étapes commencées, du plus récent au plus ancien. Page numérotée à partir de 1. */
    @GetMapping("/feed")
    public PageResponse<FeedEntry> page(@RequestParam(defaultValue = "1") int page) {
        return feed.page(page);
    }

    /** Tous les albums (sans contenu) : la carte les affiche tous ; {@code page} est leur page dans le fil. */
    @GetMapping("/map")
    public List<GallerySummary> map() {
        return feed.map();
    }

    /** Date du jour du serveur et page de chaque étape commencée dans le fil. */
    @GetMapping("/feed/positions")
    public FeedPositions positions() {
        return feed.positions();
    }
}
