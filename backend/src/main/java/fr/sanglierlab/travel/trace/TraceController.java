package fr.sanglierlab.travel.trace;

import fr.sanglierlab.travel.trace.dto.TraceMapDto;
import fr.sanglierlab.travel.trace.dto.TracePointDto;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Restitution publique des tracés, pour la carte.
 */
@RestController
@RequestMapping("/api/trace")
public class TraceController {

    private final TraceService service;

    public TraceController(TraceService service) {
        this.service = service;
    }

    /**
     * Tracés à dessiner, éventuellement restreints à une source ou à une
     * période. Les paramètres sont facultatifs : sans eux, tout le voyage.
     */
    @GetMapping
    public TraceMapDto map(
            @RequestParam(required = false) TraceSource source,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return service.buildMap(source, from, to);
    }

    /** Positions d'un vol ou d'une traversée. */
    @GetMapping("/trips/{tripId}")
    public List<TracePointDto> byTrip(@PathVariable Long tripId) {
        return service.listByTrip(tripId);
    }
}
