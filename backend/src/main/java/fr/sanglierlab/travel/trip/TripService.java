package fr.sanglierlab.travel.trip;

import fr.sanglierlab.travel.common.BadRequestException;
import fr.sanglierlab.travel.common.NotFoundException;
import fr.sanglierlab.travel.trace.TracePointRepository;
import fr.sanglierlab.travel.trip.dto.TripDto;
import fr.sanglierlab.travel.trip.dto.TripForm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Déclaration et cycle de vie des vols et traversées.
 */
@Service
public class TripService {

    private static final Logger log = LoggerFactory.getLogger(TripService.class);

    private final TripRepository trips;
    private final TracePointRepository points;

    public TripService(TripRepository trips, TracePointRepository points) {
        this.trips = trips;
        this.points = points;
    }

    // -------------------------------------------------------------- lecture

    @Transactional(readOnly = true)
    public List<TripDto> list() {
        return trips.findAllByOrderByScheduledStartDesc().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public TripDto get(Long id) {
        return toDto(require(id));
    }

    // ------------------------------------------------------------- écriture

    @Transactional
    public TripDto create(TripForm form) {
        Trip trip = new Trip(
                form.type(),
                normalize(form.type(), form.identifier()),
                trimToNull(form.label()),
                form.scheduledStart(),
                form.color());

        Trip saved = trips.save(trip);
        log.info("Trajet déclaré : {} {} au départ de {}",
                saved.getType(), saved.getIdentifier(), saved.getScheduledStart());
        return toDto(saved);
    }

    @Transactional
    public TripDto update(Long id, TripForm form) {
        Trip trip = require(id);
        trip.setType(form.type());
        trip.setIdentifier(normalize(form.type(), form.identifier()));
        trip.setLabel(trimToNull(form.label()));
        trip.setScheduledStart(form.scheduledStart());
        trip.setColor(form.color());
        return toDto(trip);
    }

    /**
     * Suppression du trajet.
     *
     * Les positions déjà recueillies sont conservées : la contrainte de clé
     * étrangère les détache sans les effacer. Retirer un vol de la
     * configuration ne doit pas amputer le tracé du voyage.
     */
    @Transactional
    public void delete(Long id) {
        Trip trip = require(id);
        trips.delete(trip);
        log.info("Trajet {} supprimé ; ses positions sont conservées", id);
    }

    // ---------------------------------------------------------- transitions

    /** Démarrage manuel, sans attendre l'heure prévue. */
    @Transactional
    public TripDto start(Long id) {
        Trip trip = require(id);
        if (trip.getStatus() == TripStatus.ACTIVE) {
            throw new BadRequestException("Ce trajet est déjà en cours");
        }
        trip.start();
        log.info("Trajet {} démarré manuellement", id);
        return toDto(trip);
    }

    /** Clôture manuelle, lorsque l'arrivée n'a pas été détectée. */
    @Transactional
    public TripDto finish(Long id) {
        Trip trip = require(id);
        if (trip.getStatus() == TripStatus.FINISHED) {
            throw new BadRequestException("Ce trajet est déjà terminé");
        }
        trip.finish();
        log.info("Trajet {} terminé manuellement", id);
        return toDto(trip);
    }

    /** Retour à l'état planifié, en cas de report. */
    @Transactional
    public TripDto reset(Long id) {
        Trip trip = require(id);
        trip.reset();
        log.info("Trajet {} replanifié", id);
        return toDto(trip);
    }

    // ------------------------------------------- appelé par l'ordonnanceur

    @Transactional
    public void activateDueTrips() {
        for (Trip trip : trips.findDueForStart(Instant.now())) {
            trip.start();
            log.info("Trajet {} {} activé : heure de départ atteinte",
                    trip.getType(), trip.getIdentifier());
        }
    }

    @Transactional
    public void finishStaleTrips(long maximumHours) {
        Instant threshold = Instant.now().minusSeconds(maximumHours * 3600);
        for (Trip trip : trips.findStaleActive(threshold)) {
            trip.finish();
            log.info("Trajet {} {} clos automatiquement après {} h",
                    trip.getType(), trip.getIdentifier(), maximumHours);
        }
    }

    @Transactional(readOnly = true)
    public List<Trip> activeTrips() {
        return trips.findByStatus(TripStatus.ACTIVE);
    }

    @Transactional
    public void markPolled(Long tripId) {
        trips.findById(tripId).ifPresent(Trip::markPolled);
    }

    // ---------------------------------------------------------------- interne

    private Trip require(Long id) {
        return trips.findById(id)
                .orElseThrow(() -> NotFoundException.of("Trajet", id));
    }

    private TripDto toDto(Trip trip) {
        long count = trip.getId() == null
                ? 0
                : points.findByTripIdOrderByMeasuredAtAsc(trip.getId()).size();
        return TripDto.from(trip, count);
    }

    /**
     * Un indicatif d'appel s'écrit en majuscules et sans espaces ; un MMSI est
     * une suite de chiffres. Normaliser à la saisie évite que « af 274 » et
     * « AF274 » ne désignent deux vols distincts.
     */
    private String normalize(TripType type, String identifier) {
        String cleaned = identifier.trim().replace(" ", "");
        return type == TripType.ADSB ? cleaned.toUpperCase() : cleaned;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
