package fr.sanglierlab.travel.trip;

import fr.sanglierlab.travel.trip.dto.TripDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Vols et traversées, en lecture publique : la carte a besoin de leurs
 * libellés et de leurs couleurs pour légender les tracés.
 */
@RestController
@RequestMapping("/api/trips")
public class TripController {

    private final TripService service;

    public TripController(TripService service) {
        this.service = service;
    }

    @GetMapping
    public List<TripDto> list() {
        return service.list();
    }
}
