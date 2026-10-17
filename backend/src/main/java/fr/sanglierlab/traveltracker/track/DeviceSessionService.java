package fr.sanglierlab.traveltracker.track;

import fr.sanglierlab.traveltracker.common.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sessions « Téléphone » : les points GPSLogger sont regroupés par excursion.
 * <ul>
 *   <li>Une session commence à son heure {@code scheduledDeparture} (le premier point, ou l'heure d'une coupure manuelle).</li>
 *   <li>Un point rejoint la dernière session commencée avant lui si elle n'est pas restée silencieuse plus de {@code gap}
 *       (3 h par défaut) avant ce point ; sinon il ouvre une nouvelle session.</li>
 *   <li>Aucune fusion automatique, et une coupure manuelle n'est jamais recollée : les points suivants restent dans la nouvelle session.</li>
 * </ul>
 * Toutes les heures sont en UTC.
 */
@Service
public class DeviceSessionService {

    public static final int MAX_NAME_LENGTH = 200;

    private final TrackedTripRepository trips;
    private final TrackPointRepository points;
    private final Duration gap;

    @Autowired
    public DeviceSessionService(TrackedTripRepository trips, TrackPointRepository points,
                                @Value("${app.device-session-gap-hours:3}") int gapHours) {
        this(trips, points, Duration.ofHours(Math.max(1, gapHours)));
    }

    DeviceSessionService(TrackedTripRepository trips, TrackPointRepository points, Duration gap) {
        this.trips = trips;
        this.points = points;
        this.gap = gap;
    }

    /** Une session « Téléphone » telle qu'affichée dans l'admin. */
    public record SessionResponse(long id, String name, Instant startedAt, Instant endedAt, long pointCount, boolean archived) {
    }

    // --- Rattachement des points ------------------------------------------------------------------------------

    /** Identifiant de la session à laquelle appartient un point pris à cette heure ; la session est créée si besoin. */
    public synchronized long assign(LocalDateTime at) {
        Optional<TrackedTrip> candidate = trips.findFirstByTypeAndScheduledDepartureLessThanEqualOrderByScheduledDepartureDescIdDesc(TripType.DEVICE, at);
        if (candidate.isPresent()) {
            TrackedTrip session = candidate.get();
            LocalDateTime last = points.lastRecordedAt(session.getId());
            if (last == null || !at.isAfter(last.plus(gap))) {
                return session.getId();
            }
        }
        TrackedTrip created = new TrackedTrip(TripType.DEVICE, "", at, "device-" + at);
        created.setStatus(TripStatus.FINISHED);
        return trips.save(created).getId();
    }

    // --- Administration ----------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<SessionResponse> list() {
        Map<Long, TripPointRange> ranges = points.tripRanges().stream().collect(Collectors.toMap(TripPointRange::tripId, Function.identity()));
        return trips.findByTypeOrderByScheduledDepartureDescIdDesc(TripType.DEVICE).stream()
                .map(trip -> toResponse(trip, ranges.get(trip.getId())))
                .toList();
    }

    /** Renomme la session ; un nom vide rétablit le nom par défaut (« Téléphone · date »). */
    @Transactional
    public SessionResponse rename(long id, String name) {
        TrackedTrip session = find(id);
        String clean = name == null ? "" : name.strip().replaceAll("\\p{Cntrl}", "");
        if (clean.length() > MAX_NAME_LENGTH) {
            throw ApiException.badRequest("Le nom est trop long (" + MAX_NAME_LENGTH + " caractères au plus).");
        }
        session.setLabel(clean);
        trips.save(session);
        return toResponse(session, rangeOf(id));
    }

    /**
     * Coupe la session : les points pris à partir de {@code at} passent dans une nouvelle session. L'heure doit tomber dans la session
     * (après son début, avant le début d'une session suivante) ; elle peut être postérieure au dernier point (« maintenant » :
     * la nouvelle session reçoit alors les prochains points).
     */
    @Transactional
    public synchronized SessionResponse split(long id, Instant at) {
        TrackedTrip session = find(id);
        LocalDateTime cut = LocalDateTime.ofInstant(at, ZoneOffset.UTC);
        Optional<TrackedTrip> owner = trips.findFirstByTypeAndScheduledDepartureLessThanEqualOrderByScheduledDepartureDescIdDesc(TripType.DEVICE, cut);
        if (owner.isEmpty() || !owner.get().getId().equals(session.getId()) || !cut.isAfter(session.getScheduledDeparture())) {
            throw ApiException.badRequest("Choisissez un moment situé dans cette session, après son début.");
        }
        TrackedTrip created = new TrackedTrip(TripType.DEVICE, "", cut, "device-" + cut);
        created.setStatus(TripStatus.FINISHED);
        created = trips.save(created);
        points.moveFrom(session.getId(), created.getId(), cut);
        return toResponse(created, rangeOf(created.getId()));
    }

    /** Supprime la session et ses positions. */
    @Transactional
    public synchronized void delete(long id) {
        TrackedTrip session = find(id);
        points.deleteByTripId(id);
        trips.delete(session);
    }

    // -----------------------------------------------------------------------

    private TrackedTrip find(long id) {
        return trips.findById(id)
                .filter(trip -> trip.getType() == TripType.DEVICE)
                .orElseThrow(() -> ApiException.notFound("Session"));
    }

    private TripPointRange rangeOf(long id) {
        return points.tripRanges().stream().filter(range -> range.tripId().equals(id)).findFirst().orElse(null);
    }

    private static SessionResponse toResponse(TrackedTrip trip, TripPointRange range) {
        Instant start = (range == null ? trip.getScheduledDeparture() : range.first()).toInstant(ZoneOffset.UTC);
        Instant end = range == null ? null : range.last().toInstant(ZoneOffset.UTC);
        String name = trip.getLabel().isBlank() ? null : trip.getLabel();
        return new SessionResponse(trip.getId(), name, start, end, range == null ? 0 : range.count(), trip.isArchived());
    }
}
