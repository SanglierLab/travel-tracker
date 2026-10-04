package fr.sanglierlab.traveltracker.track;

import java.time.Instant;
import java.util.List;

/**
 * Tracé affiché sur la carte publique.
 *
 * @param segments lignes à dessiner, de la plus ancienne à la plus récente
 * @param last     dernière position connue (toutes sources confondues), ou null s'il n'y a aucun point
 */
public record TrackResponse(List<Segment> segments, LastPosition last) {

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
