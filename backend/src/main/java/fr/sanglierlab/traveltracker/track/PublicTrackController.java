package fr.sanglierlab.traveltracker.track;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Partie publique : lecture seule, sans authentification. */
@RestController
@RequestMapping("/api/public")
public class PublicTrackController {

    private final TrackQueryService track;

    public PublicTrackController(TrackQueryService track) {
        this.track = track;
    }

    /** Lignes à dessiner sur la carte (déjà découpées et allégées) et dernière position connue. */
    @GetMapping("/track")
    public TrackResponse track() {
        return track.publicTrack();
    }
}
