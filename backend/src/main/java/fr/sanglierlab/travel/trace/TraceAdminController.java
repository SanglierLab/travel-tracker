package fr.sanglierlab.travel.trace;

import fr.sanglierlab.travel.common.PageDto;
import fr.sanglierlab.travel.trace.dto.TracePointDto;
import fr.sanglierlab.travel.trace.dto.TracePointUpdateForm;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Correction du tracé : consultation, repositionnement et suppression des
 * relevés erronés.
 */
@RestController
@RequestMapping("/api/admin/trace")
public class TraceAdminController {

    private final TraceService service;

    public TraceAdminController(TraceService service) {
        this.service = service;
    }

    /** Liste paginée, la plus récente d'abord. */
    @GetMapping
    public PageDto<TracePointDto> list(@RequestParam(required = false) TraceSource source,
                                       @RequestParam(required = false) Integer page,
                                       @RequestParam(required = false) Integer size) {
        return service.listForAdmin(source, page, size);
    }

    /** Alimente la page dédiée à la correction d'un point. */
    @GetMapping("/{id}")
    public TracePointDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public TracePointDto update(@PathVariable Long id,
                                @Valid @RequestBody TracePointUpdateForm form) {
        return service.update(id, form);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Suppression groupée, pour une série de relevés erratiques. */
    @PostMapping("/delete-batch")
    public Map<String, Integer> deleteBatch(@RequestBody List<Long> ids) {
        return Map.of("deleted", service.deleteAll(ids));
    }
}
