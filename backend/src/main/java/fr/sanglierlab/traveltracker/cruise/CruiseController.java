package fr.sanglierlab.traveltracker.cruise;

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

/** Administration des croisières suivies (session admin et CSRF, comme le reste de /api/admin). */
@RestController
@RequestMapping("/api/admin/cruises")
public class CruiseController {

    private final CruiseService cruises;

    public CruiseController(CruiseService cruises) {
        this.cruises = cruises;
    }

    @GetMapping
    public List<CruiseResponse> list() {
        return cruises.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CruiseResponse create(@Valid @RequestBody CruiseRequest request) {
        return cruises.create(request);
    }

    /** Bouton « on » : démarre le suivi (409 si une autre croisière est déjà suivie). */
    @PostMapping("/{id}/start")
    public CruiseResponse start(@PathVariable long id) {
        return cruises.start(id);
    }

    /** Bouton « off » : arrête le suivi. */
    @PostMapping("/{id}/stop")
    public CruiseResponse stop(@PathVariable long id) {
        return cruises.stop(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        cruises.delete(id);
    }
}
