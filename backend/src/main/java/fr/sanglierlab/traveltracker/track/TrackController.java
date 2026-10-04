package fr.sanglierlab.traveltracker.track;

import fr.sanglierlab.traveltracker.common.ApiException;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** Réception des positions envoyées par le téléphone. Protégé par le token d'API (en-tête X-API-Token). */
@RestController
@RequestMapping("/api/track")
public class TrackController {

    private final TrackPointService points;

    public TrackController(TrackPointService points) {
        this.points = points;
    }

    @PostMapping("/points")
    public TrackPointResponse addPoint(@Valid @RequestBody TrackPointRequest request) {
        Instant recordedAt;
        try {
            recordedAt = TrackTime.parse(request.time(), request.timestamp(), Instant.now());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(e.getMessage());
        }
        // La source est toujours « téléphone » ici : les points ADS-B et AIS seront insérés par le futur batch interne.
        TrackPointService.Result result = points.ingest(TrackSource.DEVICE, request.latitude(), request.longitude(), recordedAt);
        return new TrackPointResponse(result == TrackPointService.Result.CREATED ? "created" : "duplicate");
    }
}
