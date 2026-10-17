package fr.sanglierlab.traveltracker.track;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/** Archivage et export GPX d'un trajet, quel que soit son type (session admin et CSRF, comme le reste de /api/admin). */
@RestController
@RequestMapping("/api/admin/trips")
public class TripAdminController {

    private static final MediaType GPX = MediaType.parseMediaType("application/gpx+xml");

    private final TripAdminService trips;

    public TripAdminController(TripAdminService trips) {
        this.trips = trips;
    }

    @PostMapping("/{id}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable long id) {
        trips.archive(id);
    }

    @PostMapping("/{id}/unarchive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unarchive(@PathVariable long id) {
        trips.unarchive(id);
    }

    @GetMapping("/{id}/gpx")
    public ResponseEntity<byte[]> gpx(@PathVariable long id) {
        TripAdminService.GpxFile file = trips.gpx(id);
        return ResponseEntity.ok()
                .contentType(GPX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(file.content().getBytes(StandardCharsets.UTF_8));
    }
}
