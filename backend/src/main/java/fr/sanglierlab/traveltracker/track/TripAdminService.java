package fr.sanglierlab.traveltracker.track;

import fr.sanglierlab.traveltracker.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Actions communes à tous les trajets suivis (vols, croisières, sessions LiveTrack) : archivage et export GPX. */
@Service
public class TripAdminService {

    /** Un fichier GPX prêt à être téléchargé. */
    public record GpxFile(String fileName, String content) {
    }

    private final TrackedTripRepository trips;
    private final TrackPointRepository points;

    public TripAdminService(TrackedTripRepository trips, TrackPointRepository points) {
        this.trips = trips;
        this.points = points;
    }

    /** Archive le trajet. Refusé tant que son suivi est actif. Le lien de partage d'une session LiveTrack est effacé. */
    @Transactional
    public void archive(long id) {
        TrackedTrip trip = find(id);
        if (trip.getStatus() == TripStatus.ACTIVE) {
            throw ApiException.conflict("Le suivi de ce trajet est actif : arrêtez-le avant de l'archiver.");
        }
        trip.setArchived(true);
        if (trip.getType() == TripType.LIVETRACK) {
            trip.clearAccessToken();
        }
        trips.save(trip);
    }

    @Transactional
    public void unarchive(long id) {
        TrackedTrip trip = find(id);
        trip.setArchived(false);
        trips.save(trip);
    }

    @Transactional(readOnly = true)
    public GpxFile gpx(long id) {
        TrackedTrip trip = find(id);
        List<TrackPoint> list = points.findByTripIdOrderByRecordedAtAscIdAsc(id);
        if (list.isEmpty()) {
            throw ApiException.conflict("Ce trajet n'a aucune position à exporter.");
        }
        return new GpxFile(TripGpx.fileName(trip), TripGpx.build(trip.getLabel(), list));
    }

    private TrackedTrip find(long id) {
        return trips.findById(id).orElseThrow(() -> ApiException.notFound("Trajet"));
    }
}
