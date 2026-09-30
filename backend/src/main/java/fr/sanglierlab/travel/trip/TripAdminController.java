package fr.sanglierlab.travel.trip;

import fr.sanglierlab.travel.trip.dto.TripDto;
import fr.sanglierlab.travel.trip.dto.TripForm;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Déclaration des vols et traversées, et pilotage manuel de leur suivi.
 */
@RestController
@RequestMapping("/api/admin/trips")
public class TripAdminController {

    private final TripService service;

    public TripAdminController(TripService service) {
        this.service = service;
    }

    @GetMapping
    public List<TripDto> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public TripDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TripDto create(@Valid @RequestBody TripForm form) {
        return service.create(form);
    }

    @PutMapping("/{id}")
    public TripDto update(@PathVariable Long id, @Valid @RequestBody TripForm form) {
        return service.update(id, form);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Démarrage anticipé, lorsque le départ a lieu plus tôt que prévu. */
    @PostMapping("/{id}/start")
    public TripDto start(@PathVariable Long id) {
        return service.start(id);
    }

    /** Clôture manuelle, si l'arrivée n'a pas été détectée. */
    @PostMapping("/{id}/finish")
    public TripDto finish(@PathVariable Long id) {
        return service.finish(id);
    }

    /** Replanification après un report. */
    @PostMapping("/{id}/reset")
    public TripDto reset(@PathVariable Long id) {
        return service.reset(id);
    }
}
