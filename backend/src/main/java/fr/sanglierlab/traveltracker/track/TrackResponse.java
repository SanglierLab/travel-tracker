package fr.sanglierlab.traveltracker.track;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Tracé affiché sur la carte publique.
 *
 * @param segments lignes à dessiner, de la plus ancienne à la plus récente
 * @param last     dernière position connue (toutes sources confondues), ou null s'il n'y a aucun point
 * @param lateAfterSeconds par source, délai (en secondes) sans nouvelle position au-delà duquel la dernière position est « en retard »
 *                 (la page compare à l'heure courante, donc l'état évolue sans nouvel appel)
 */
public record TrackResponse(List<Segment> segments, LastPosition last, Map<TrackSource, Long> lateAfterSeconds) {

    public TrackResponse(List<Segment> segments, LastPosition last) {
        this(segments, last, Map.of());
    }

    public TrackResponse withLateAfter(Map<TrackSource, Long> lateAfterSeconds) {
        return new TrackResponse(segments, last, lateAfterSeconds);
    }

    /**
     * Une ligne continue.
     *
     * @param source la source (le style dépend d'elle sur la carte)
     * @param tripId le trajet suivi (vol, traversée) ou null pour le téléphone
     * @param start  heure du premier point (UTC)
     * @param end    heure du dernier point (UTC)
     * @param points points simplifiés, chacun étant [latitude, longitude]. La longitude est « continue » : elle peut
     *               dépasser 180 ou -180 quand la ligne traverse la ligne de changement de date (le tracé reste d'un seul tenant).
     */
    public record Segment(TrackSource source, Long tripId, Instant start, Instant end, List<double[]> points) {
    }

    public record LastPosition(TrackSource source, double latitude, double longitude, Instant recordedAt) {
    }
}
