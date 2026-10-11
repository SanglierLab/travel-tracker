package fr.sanglierlab.traveltracker.stage;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Partie publique : lecture seule, sans authentification. */
@RestController
@RequestMapping("/api/public")
public class PublicStageController {

    private final StageService stages;

    public PublicStageController(StageService stages) {
        this.stages = stages;
    }

    /** Toutes les étapes du voyage (passées et à venir) dans l'ordre manuel, avec leur tracé éventuel. */
    @GetMapping("/itinerary")
    public List<StageResponse> itinerary() {
        return stages.publicItinerary();
    }
}
