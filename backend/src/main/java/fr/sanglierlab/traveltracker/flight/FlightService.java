package fr.sanglierlab.traveltracker.flight;

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
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Vols suivis : enregistrement, démarrage et arrêt du suivi, suppression.
 * Un seul suivi peut être actif à la fois.
 */
@Service
public class FlightService {

    private static final Pattern FLIGHT_NUMBER = Pattern.compile("[A-Z0-9]{2,8}");

    private final TrackedTripRepository trips;
    private final TrackPointRepository points;
    private final AdsbBatch batch;

    public FlightService(TrackedTripRepository trips, TrackPointRepository points, AdsbBatch batch) {
        this.trips = trips;
        this.points = points;
        this.batch = batch;
    }

    @Transactional(readOnly = true)
    public List<FlightResponse> list() {
        Map<Long, TripPointStats> stats = statsByTrip();
        return trips.findByTypeOrderByScheduledDepartureDescIdDesc(TripType.PLANE).stream()
                .map(trip -> toResponse(trip, stats.get(trip.getId())))
                .toList();
    }

    public FlightResponse create(FlightRequest request) {
        String number = normalize(request.identifier());
        LocalDateTime departure = LocalDateTime.of(request.date(), request.time() != null ? request.time() : LocalTime.MIDNIGHT);
        TrackedTrip trip = trips.save(new TrackedTrip(TripType.PLANE, "Vol " + number, departure, number));
        return toResponse(trip, null);
    }

    /** Démarre le suivi. Refusé si un autre vol est déjà suivi : il faut d'abord l'arrêter. */
    public synchronized FlightResponse start(long id) {
        TrackedTrip trip = find(id);
        trips.findFirstByTypeAndStatus(TripType.PLANE, TripStatus.ACTIVE)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw ApiException.conflict("Le suivi du vol " + other.getIdentifier()
                            + " est déjà actif. Arrêtez-le avant d'en démarrer un autre.");
                });
        trip.setStatus(TripStatus.ACTIVE);
        trips.save(trip);
        // La base est à jour avant le premier cycle : le batch relit l'état du vol.
        batch.start(id);
        return toResponse(trip, statsByTrip().get(id));
    }

    /** Arrête le suivi (les positions déjà enregistrées sont conservées). Sans effet si le vol n'est pas suivi. */
    public synchronized FlightResponse stop(long id) {
        TrackedTrip trip = find(id);
        if (trip.getStatus() == TripStatus.ACTIVE) {
            trip.setStatus(TripStatus.FINISHED);
            trips.save(trip);
        }
        batch.stop(id);
        return toResponse(trip, statsByTrip().get(id));
    }

    /** Supprime le vol et ses positions. Refusé tant que son suivi est actif. */
    @Transactional
    public synchronized void delete(long id) {
        TrackedTrip trip = find(id);
        if (trip.getStatus() == TripStatus.ACTIVE) {
            throw ApiException.conflict("Le suivi de ce vol est actif : arrêtez-le avant de le supprimer.");
        }
        points.deleteByTripId(id);
        trips.delete(trip);
    }

    // -----------------------------------------------------------------------

    private TrackedTrip find(long id) {
        return trips.findById(id)
                .filter(trip -> trip.getType() == TripType.PLANE)
                .orElseThrow(() -> ApiException.notFound("Vol"));
    }

    private Map<Long, TripPointStats> statsByTrip() {
        return points.tripStats().stream().collect(Collectors.toMap(TripPointStats::tripId, Function.identity()));
    }

    /** « afr 1234 » -> « AFR1234 ». Un numéro de vol compte 2 à 8 lettres ou chiffres. */
    static String normalize(String raw) {
        String number = raw.strip().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        if (!FLIGHT_NUMBER.matcher(number).matches()) {
            throw ApiException.badRequest("Numéro de vol invalide : 2 à 8 lettres ou chiffres, par exemple AFR1234.");
        }
        return number;
    }

    private static FlightResponse toResponse(TrackedTrip trip, TripPointStats stats) {
        return new FlightResponse(trip.getId(), trip.getIdentifier(), trip.getScheduledDeparture().toInstant(ZoneOffset.UTC),
                trip.getStatus(), stats == null ? 0 : stats.count(),
                stats == null ? null : stats.lastRecordedAt().toInstant(ZoneOffset.UTC));
    }
}
