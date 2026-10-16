package fr.sanglierlab.traveltracker.livetrack;

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
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sessions LiveTrack suivies : enregistrement d'un lien, démarrage et arrêt du suivi, suppression.
 * Un seul suivi LiveTrack peut être actif à la fois. Chaque activité de la montre crée un nouveau lien : il faut l'enregistrer à chaque fois.
 */
@Service
public class LiveTrackService {

    private final TrackedTripRepository trips;
    private final TrackPointRepository points;
    private final LiveTrackBatch batch;

    public LiveTrackService(TrackedTripRepository trips, TrackPointRepository points, LiveTrackBatch batch) {
        this.trips = trips;
        this.points = points;
        this.batch = batch;
    }

    @Transactional(readOnly = true)
    public List<LiveTrackResponse> list() {
        Map<Long, TripPointStats> stats = statsByTrip();
        return trips.findByTypeOrderByScheduledDepartureDescIdDesc(TripType.LIVETRACK).stream()
                .map(trip -> toResponse(trip, stats.get(trip.getId())))
                .toList();
    }

    public LiveTrackResponse create(LiveTrackRequest request) {
        LiveTrackLink link = LiveTrackLink.parse(request.link()).orElseThrow(() -> ApiException.badRequest(
                "Lien LiveTrack invalide : collez le lien complet, de la forme https://livetrack.garmin.com/session/…/token/…"));
        if (trips.existsByTypeAndIdentifier(TripType.LIVETRACK, link.sessionId())) {
            throw ApiException.conflict("Cette session LiveTrack est déjà enregistrée.");
        }
        String name = request.name() == null ? "" : request.name().strip();
        String label = name.isEmpty() ? "LiveTrack " + LiveTrackLink.shortId(link.sessionId()) : name;
        TrackedTrip trip = trips.save(new TrackedTrip(TripType.LIVETRACK, label,
                LocalDateTime.now(ZoneOffset.UTC), link.sessionId(), link.token()));
        return toResponse(trip, null);
    }

    /** Démarre le suivi. Refusé si une autre session est déjà suivie : il faut d'abord l'arrêter. */
    public synchronized LiveTrackResponse start(long id) {
        TrackedTrip trip = find(id);
        trips.findFirstByTypeAndStatus(TripType.LIVETRACK, TripStatus.ACTIVE)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw ApiException.conflict("Le suivi de la session « " + other.getLabel()
                            + " » est déjà actif. Arrêtez-le avant d'en démarrer un autre.");
                });
        trip.setStatus(TripStatus.ACTIVE);
        trips.save(trip);
        // La base est à jour avant le premier cycle : le batch relit l'état de la session.
        batch.start(id);
        return toResponse(trip, statsByTrip().get(id));
    }

    /** Arrête le suivi (les positions déjà enregistrées sont conservées). Sans effet si la session n'est pas suivie. */
    public synchronized LiveTrackResponse stop(long id) {
        TrackedTrip trip = find(id);
        if (trip.getStatus() == TripStatus.ACTIVE) {
            trip.setStatus(TripStatus.FINISHED);
            trips.save(trip);
        }
        batch.stop(id);
        return toResponse(trip, statsByTrip().get(id));
    }

    /** Supprime la session, son jeton et ses positions. Refusé tant que son suivi est actif. */
    @Transactional
    public synchronized void delete(long id) {
        TrackedTrip trip = find(id);
        if (trip.getStatus() == TripStatus.ACTIVE) {
            throw ApiException.conflict("Le suivi de cette session est actif : arrêtez-le avant de la supprimer.");
        }
        points.deleteByTripId(id);
        trips.delete(trip);
    }

    // -----------------------------------------------------------------------

    private TrackedTrip find(long id) {
        return trips.findById(id)
                .filter(trip -> trip.getType() == TripType.LIVETRACK)
                .orElseThrow(() -> ApiException.notFound("Session LiveTrack"));
    }

    private Map<Long, TripPointStats> statsByTrip() {
        return points.tripStats().stream().collect(Collectors.toMap(TripPointStats::tripId, Function.identity()));
    }

    private static LiveTrackResponse toResponse(TrackedTrip trip, TripPointStats stats) {
        return new LiveTrackResponse(trip.getId(), trip.getLabel(), LiveTrackLink.shortId(trip.getIdentifier()),
                trip.getScheduledDeparture().toInstant(ZoneOffset.UTC), trip.getStatus(),
                stats == null ? 0 : stats.count(),
                stats == null ? null : stats.lastRecordedAt().toInstant(ZoneOffset.UTC));
    }
}
