package fr.sanglierlab.traveltracker.stage;

import fr.sanglierlab.traveltracker.gallery.Direction;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** Administration des étapes (session admin et CSRF, comme le reste de /api/admin). */
@RestController
@RequestMapping("/api/admin/stages")
public class AdminStageController {

    private final StageService stages;

    public AdminStageController(StageService stages) {
        this.stages = stages;
    }

    @GetMapping
    public List<StageResponse> list() {
        return stages.list();
    }

    @GetMapping("/{id}")
    public StageResponse get(@PathVariable long id) {
        return stages.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StageResponse create(@Valid @RequestBody StageRequest request) {
        return stages.create(request);
    }

    @PutMapping("/{id}")
    public StageResponse update(@PathVariable long id, @Valid @RequestBody StageRequest request) {
        return stages.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        stages.delete(id);
    }

    /** Monte ou descend l'étape d'un cran : {@code ?direction=UP} ou {@code DOWN}. */
    @PostMapping("/{id}/move")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void move(@PathVariable long id, @RequestParam Direction direction) {
        stages.move(id, direction);
    }

    /** Recale l'ordre manuel sur les dates ; renvoie la liste dans son nouvel ordre. */
    @PostMapping("/sort-by-date")
    public List<StageResponse> sortByDate() {
        return stages.sortByDate();
    }

    /** Envoie un GPX (champ « file ») pour un trajet. {@code useEndpoints=true} reprend les extrémités du tracé comme positions. */
    @PostMapping(value = "/{id}/gpx", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public StageResponse uploadGpx(@PathVariable long id, @RequestParam("file") MultipartFile file,
                                   @RequestParam(defaultValue = "false") boolean useEndpoints) throws IOException {
        try (InputStream in = file.getInputStream()) {
            return stages.uploadGpx(id, in, file.getSize(), useEndpoints);
        }
    }

    @DeleteMapping("/{id}/gpx")
    public StageResponse removeGpx(@PathVariable long id) {
        return stages.removeGpx(id);
    }
}
