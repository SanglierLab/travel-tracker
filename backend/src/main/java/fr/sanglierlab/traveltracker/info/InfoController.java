package fr.sanglierlab.traveltracker.info;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Page Config de l'administration : informations en lecture seule (protégée par /api/admin/**). */
@RestController
@RequestMapping("/api/admin/info")
public class InfoController {

    private final InfoService service;

    public InfoController(InfoService service) {
        this.service = service;
    }

    @GetMapping
    public InfoResponse info() {
        return service.info();
    }
}
