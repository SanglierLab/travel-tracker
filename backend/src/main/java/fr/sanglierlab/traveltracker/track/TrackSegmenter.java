package fr.sanglierlab.traveltracker.track;

import fr.sanglierlab.traveltracker.track.TrackResponse.LastPosition;
import fr.sanglierlab.traveltracker.track.TrackResponse.Segment;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Transforme les points de la base en lignes à dessiner.
 * <ul>
 *   <li>Les points du téléphone (sans trajet) forment une ligne, COUPÉE quand deux points consécutifs sont espacés de
 *       plus de {@code gap} (téléphone éteint, vol, etc.) : pas de trait absurde à travers un trou.</li>
 *   <li>Les points d'un trajet suivi (vol, traversée) forment UNE ligne par trajet, sans coupure.</li>
 *   <li>Chaque ligne est ensuite allégée (voir {@link PathSimplifier}) ; si le total reste trop gros, la tolérance
 *       augmente jusqu'à rentrer dans le budget : la réponse reste légère même après des semaines de voyage.</li>
 * </ul>
 */
public final class TrackSegmenter {

    /** Points plus proches que cela du précédent : tremblement du GPS à l'arrêt. */
    static final double MIN_STEP_METERS = 5;
    /** Écart toléré entre la ligne simplifiée et les vrais points. */
    static final double TOLERANCE_METERS = 25;
    /** Nombre maximal de points renvoyés au total. */
    static final int MAX_POINTS = 20_000;
    private static final double MAX_TOLERANCE_METERS = 10_000;

    private TrackSegmenter() {
    }

    private record GroupKey(TrackSource source, Long tripId) {
    }

    private record Raw(TrackSource source, Long tripId, Instant start, Instant end, List<double[]> path) {
    }

    /** @param rows tous les points, triés par heure croissante */
    public static TrackResponse build(List<TrackRow> rows, Duration gap) {
        Map<GroupKey, List<TrackRow>> groups = new LinkedHashMap<>();
        for (TrackRow row : rows) {
            groups.computeIfAbsent(new GroupKey(row.source(), row.tripId()), key -> new ArrayList<>()).add(row);
        }

        List<Raw> raws = new ArrayList<>();
        for (Map.Entry<GroupKey, List<TrackRow>> group : groups.entrySet()) {
            boolean cutOnGaps = group.getKey().tripId() == null;
            List<TrackRow> current = new ArrayList<>();
            Instant previous = null;
            for (TrackRow row : group.getValue()) {
                Instant at = row.instant();
                if (cutOnGaps && previous != null && Duration.between(previous, at).compareTo(gap) > 0) {
                    raws.add(raw(group.getKey(), current));
                    current = new ArrayList<>();
                }
                current.add(row);
                previous = at;
            }
            if (!current.isEmpty()) {
                raws.add(raw(group.getKey(), current));
            }
        }

        double tolerance = TOLERANCE_METERS;
        List<Segment> segments;
        while (true) {
            final double currentTolerance = tolerance;
            segments = new ArrayList<>();
            int total = 0;
            for (Raw raw : raws) {
                List<double[]> points = PathSimplifier.simplify(PathSimplifier.thin(raw.path(), MIN_STEP_METERS), currentTolerance);
                segments.add(new Segment(raw.source(), raw.tripId(), raw.start(), raw.end(), points));
                total += points.size();
            }
            if (total <= MAX_POINTS || tolerance >= MAX_TOLERANCE_METERS) {
                break;
            }
            tolerance *= 2;
        }
        segments = withinBudget(segments);
        segments.sort(Comparator.comparing(Segment::start));

        LastPosition last = rows.stream()
                .max(Comparator.comparing(TrackRow::instant))
                .map(row -> new LastPosition(row.source(), row.latitude().doubleValue(), row.longitude().doubleValue(), row.instant()))
                .orElse(null);
        return new TrackResponse(segments, last);
    }

    /**
     * Dernier recours : si même une grande tolérance laisse trop de points (tracé très chaotique), on garde des
     * points régulièrement espacés, au prorata de la taille de chaque ligne. Premier et dernier points sont conservés.
     */
    private static List<Segment> withinBudget(List<Segment> segments) {
        int total = segments.stream().mapToInt(segment -> segment.points().size()).sum();
        if (total <= MAX_POINTS) {
            return segments;
        }
        List<Segment> reduced = new ArrayList<>();
        for (Segment segment : segments) {
            List<double[]> points = segment.points();
            int allowed = Math.max(2, (int) ((long) MAX_POINTS * points.size() / total));
            if (points.size() <= allowed) {
                reduced.add(segment);
                continue;
            }
            List<double[]> sampled = new ArrayList<>(allowed);
            for (int i = 0; i < allowed; i++) {
                sampled.add(points.get((int) ((long) i * (points.size() - 1) / (allowed - 1))));
            }
            reduced.add(new Segment(segment.source(), segment.tripId(), segment.start(), segment.end(), sampled));
        }
        return reduced;
    }

    private static Raw raw(GroupKey key, List<TrackRow> rows) {
        return new Raw(key.source(), key.tripId(), rows.get(0).instant(), rows.get(rows.size() - 1).instant(), continuousPath(rows));
    }

    /**
     * Longitudes « continues » : quand deux points consécutifs passent de 179,9° à -179,9° (ligne de changement de date),
     * on poursuit à 180,1° au lieu de revenir de l'autre côté du monde, sinon la carte tracerait un trait de bout en bout.
     */
    private static List<double[]> continuousPath(List<TrackRow> rows) {
        List<double[]> path = new ArrayList<>(rows.size());
        double offset = 0;
        double previousLongitude = Double.NaN;
        for (TrackRow row : rows) {
            double latitude = row.latitude().doubleValue();
            double longitude = row.longitude().doubleValue();
            if (!Double.isNaN(previousLongitude)) {
                double difference = longitude - previousLongitude;
                if (difference > 180) {
                    offset -= 360;
                } else if (difference < -180) {
                    offset += 360;
                }
            }
            previousLongitude = longitude;
            path.add(new double[]{latitude, longitude + offset});
        }
        return path;
    }
}
