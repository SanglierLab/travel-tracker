package fr.sanglierlab.traveltracker.track;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** Administration des sessions « Téléphone » (session admin et CSRF). Archivage et export : voir {@link TripAdminController}. */
@RestController
@RequestMapping("/api/admin/device-sessions")
public class DeviceSessionController {

    /** @param name nouveau nom ; vide ou absent : nom par défaut */
    public record RenameRequest(String name) {
    }

    /** @param at heure de la coupure (instant ISO 8601, par exemple 2026-10-12T05:30:00Z) */
    public record SplitRequest(@NotNull Instant at) {
    }

    private final DeviceSessionService sessions;

    public DeviceSessionController(DeviceSessionService sessions) {
        this.sessions = sessions;
    }

    @GetMapping
    public List<DeviceSessionService.SessionResponse> list() {
        return sessions.list();
    }

    @PutMapping("/{id}")
    public DeviceSessionService.SessionResponse rename(@PathVariable long id, @RequestBody RenameRequest request) {
        return sessions.rename(id, request.name());
    }

    @PostMapping("/{id}/split")
    @ResponseStatus(HttpStatus.CREATED)
    public DeviceSessionService.SessionResponse split(@PathVariable long id, @Valid @RequestBody SplitRequest request) {
        return sessions.split(id, request.at());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        sessions.delete(id);
    }
}
