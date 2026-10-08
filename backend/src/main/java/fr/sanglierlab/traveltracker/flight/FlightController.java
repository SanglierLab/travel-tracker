package fr.sanglierlab.traveltracker.flight;

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

/** Administration des vols suivis (session admin et CSRF, comme le reste de /api/admin). */
@RestController
@RequestMapping("/api/admin/flights")
public class FlightController {

    private final FlightService flights;

    public FlightController(FlightService flights) {
        this.flights = flights;
    }

    @GetMapping
    public List<FlightResponse> list() {
        return flights.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FlightResponse create(@Valid @RequestBody FlightRequest request) {
        return flights.create(request);
    }

    /** Bouton « on » : démarre le suivi (409 si un autre vol est déjà suivi). */
    @PostMapping("/{id}/start")
    public FlightResponse start(@PathVariable long id) {
        return flights.start(id);
    }

    /** Bouton « off » : arrête le suivi. */
    @PostMapping("/{id}/stop")
    public FlightResponse stop(@PathVariable long id) {
        return flights.stop(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        flights.delete(id);
    }
}
