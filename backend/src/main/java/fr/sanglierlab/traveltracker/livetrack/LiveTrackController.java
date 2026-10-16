package fr.sanglierlab.traveltracker.livetrack;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Administration des sessions Garmin LiveTrack (session admin et CSRF, comme le reste de /api/admin). */
@RestController
@RequestMapping("/api/admin/livetracks")
public class LiveTrackController {

    private final LiveTrackService liveTracks;

    public LiveTrackController(LiveTrackService liveTracks) {
        this.liveTracks = liveTracks;
    }

    @GetMapping
    public List<LiveTrackResponse> list() {
        return liveTracks.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LiveTrackResponse create(@Valid @RequestBody LiveTrackRequest request) {
        return liveTracks.create(request);
    }

    /** Bouton « on » : démarre le suivi (409 si une autre session est déjà suivie). */
    @PostMapping("/{id}/start")
    public LiveTrackResponse start(@PathVariable long id) {
        return liveTracks.start(id);
    }

    /** Bouton « off » : arrête le suivi. */
    @PostMapping("/{id}/stop")
    public LiveTrackResponse stop(@PathVariable long id) {
        return liveTracks.stop(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        liveTracks.delete(id);
    }
}
