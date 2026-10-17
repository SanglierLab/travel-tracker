package fr.sanglierlab.traveltracker.cruise;

import fr.sanglierlab.traveltracker.common.ApiException;
import fr.sanglierlab.traveltracker.track.TrackPointRepository;
import fr.sanglierlab.traveltracker.track.TrackedTrip;
import fr.sanglierlab.traveltracker.track.TrackedTripRepository;
import fr.sanglierlab.traveltracker.track.TripPointStats;
import fr.sanglierlab.traveltracker.track.TripStatus;
import fr.sanglierlab.traveltracker.track.TripType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Croisières suivies (AIS) : enregistrement, démarrage et arrêt du suivi, suppression. Parallèle exact de FlightService,
 * avec le MMSI du navire à la place de l'indicatif de vol. Un seul suivi de croisière peut être actif à la fois
 * (indépendamment du suivi d'un vol).
 */
@Service
public class CruiseService {

    private static final Pattern MMSI = Pattern.compile("[0-9]{9}");

    private final TrackedTripRepository trips;
    private final TrackPointRepository points;
    private final AisBatch batch;

    public CruiseService(TrackedTripRepository trips, TrackPointRepository points, AisBatch batch) {
        this.trips = trips;
        this.points = points;
        this.batch = batch;
    }

    @Transactional(readOnly = true)
    public List<CruiseResponse> list() {
        Map<Long, TripPointStats> stats = statsByTrip();
        return trips.findByTypeOrderByScheduledDepartureDescIdDesc(TripType.BOAT).stream()
                .map(trip -> toResponse(trip, stats.get(trip.getId())))
                .toList();
    }

    public CruiseResponse create(CruiseRequest request) {
        String mmsi = normalize(request.identifier());
        LocalDateTime departure = LocalDateTime.of(request.date(), request.time() != null ? request.time() : LocalTime.MIDNIGHT);
        TrackedTrip trip = trips.save(new TrackedTrip(TripType.BOAT, "Croisière " + mmsi, departure, mmsi));
        return toResponse(trip, null);
    }

    /** Démarre le suivi. Refusé si une autre croisière est déjà suivie : il faut d'abord l'arrêter. */
    public synchronized CruiseResponse start(long id) {
        TrackedTrip trip = find(id);
        if (trip.isArchived()) {
            throw ApiException.conflict("Ce trajet est archivé : restaurez-le avant de démarrer son suivi.");
        }
        trips.findFirstByTypeAndStatus(TripType.BOAT, TripStatus.ACTIVE)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw ApiException.conflict("Le suivi du navire " + other.getIdentifier()
                            + " est déjà actif. Arrêtez-le avant d'en démarrer un autre.");
                });
        trip.setStatus(TripStatus.ACTIVE);
        trips.save(trip);
        // La base est à jour avant le premier cycle : le batch relit l'état de la croisière.
        batch.start(id);
        return toResponse(trip, statsByTrip().get(id));
    }

    /** Arrête le suivi (les positions déjà enregistrées sont conservées). Sans effet si la croisière n'est pas suivie. */
    public synchronized CruiseResponse stop(long id) {
        TrackedTrip trip = find(id);
        if (trip.getStatus() == TripStatus.ACTIVE) {
            trip.setStatus(TripStatus.FINISHED);
            trips.save(trip);
        }
        batch.stop(id);
        return toResponse(trip, statsByTrip().get(id));
    }

    /** Supprime la croisière et ses positions. Refusé tant que son suivi est actif. */
    @Transactional
    public synchronized void delete(long id) {
        TrackedTrip trip = find(id);
        if (trip.getStatus() == TripStatus.ACTIVE) {
            throw ApiException.conflict("Le suivi de cette croisière est actif : arrêtez-le avant de la supprimer.");
        }
        points.deleteByTripId(id);
        trips.delete(trip);
    }

    // -----------------------------------------------------------------------

    private TrackedTrip find(long id) {
        return trips.findById(id)
                .filter(trip -> trip.getType() == TripType.BOAT)
                .orElseThrow(() -> ApiException.notFound("Croisière"));
    }

    private Map<Long, TripPointStats> statsByTrip() {
        return points.tripStats().stream().collect(Collectors.toMap(TripPointStats::tripId, Function.identity()));
    }

    /** « 227 123 456 » -> « 227123456 ». Un MMSI compte exactement 9 chiffres. */
    static String normalize(String raw) {
        String mmsi = raw.strip().replaceAll("\\s+", "");
        if (!MMSI.matcher(mmsi).matches()) {
            throw ApiException.badRequest("MMSI invalide : 9 chiffres, par exemple 227123456.");
        }
        return mmsi;
    }

    private static CruiseResponse toResponse(TrackedTrip trip, TripPointStats stats) {
        return new CruiseResponse(trip.getId(), trip.getIdentifier(), trip.getScheduledDeparture().toInstant(ZoneOffset.UTC),
                trip.getStatus(), trip.isArchived(), stats == null ? 0 : stats.count(),
                stats == null ? null : stats.lastRecordedAt().toInstant(ZoneOffset.UTC));
    }
}
