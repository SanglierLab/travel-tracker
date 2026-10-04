package fr.sanglierlab.traveltracker.track;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Allège un tracé sans en changer l'allure : un point par minute pendant deux semaines, ce sont des dizaines de
 * milliers de points inutiles pour dessiner une ligne sur une carte.
 * Les points sont des tableaux [latitude, longitude] en degrés.
 */
public final class PathSimplifier {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private PathSimplifier() {
    }

    /** Supprime les points à moins de {@code minStepMeters} du dernier point conservé (tremblement du GPS à l'arrêt). Garde toujours le premier et le dernier. */
    public static List<double[]> thin(List<double[]> points, double minStepMeters) {
        if (points.size() <= 2) {
            return new ArrayList<>(points);
        }
        List<double[]> kept = new ArrayList<>();
        double[] last = points.get(0);
        kept.add(last);
        for (int i = 1; i < points.size() - 1; i++) {
            double[] point = points.get(i);
            if (distanceMeters(last, point) >= minStepMeters) {
                kept.add(point);
                last = point;
            }
        }
        kept.add(points.get(points.size() - 1));
        return kept;
    }

    /**
     * Algorithme de Douglas-Peucker : ne garde que les points qui s'écartent de plus de {@code toleranceMeters}
     * de la ligne droite qui les relierait. Version itérative (pas de récursion : les tracés peuvent être très longs).
     */
    public static List<double[]> simplify(List<double[]> points, double toleranceMeters) {
        int n = points.size();
        if (n <= 2) {
            return new ArrayList<>(points);
        }
        double[] x = new double[n];
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            double latitude = Math.toRadians(points.get(i)[0]);
            double longitude = Math.toRadians(points.get(i)[1]);
            x[i] = EARTH_RADIUS_METERS * longitude * Math.cos(latitude);
            y[i] = EARTH_RADIUS_METERS * latitude;
        }

        boolean[] keep = new boolean[n];
        keep[0] = true;
        keep[n - 1] = true;
        Deque<int[]> ranges = new ArrayDeque<>();
        ranges.push(new int[]{0, n - 1});
        while (!ranges.isEmpty()) {
            int[] range = ranges.pop();
            int first = range[0];
            int last = range[1];
            double farthest = -1;
            int index = -1;
            for (int i = first + 1; i < last; i++) {
                double distance = distanceToSegment(x[i], y[i], x[first], y[first], x[last], y[last]);
                if (distance > farthest) {
                    farthest = distance;
                    index = i;
                }
            }
            if (index >= 0 && farthest > toleranceMeters) {
                keep[index] = true;
                ranges.push(new int[]{first, index});
                ranges.push(new int[]{index, last});
            }
        }

        List<double[]> result = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (keep[i]) {
                result.add(points.get(i));
            }
        }
        return result;
    }

    static double distanceMeters(double[] a, double[] b) {
        double meanLatitude = Math.toRadians((a[0] + b[0]) / 2);
        double dx = Math.toRadians(b[1] - a[1]) * Math.cos(meanLatitude) * EARTH_RADIUS_METERS;
        double dy = Math.toRadians(b[0] - a[0]) * EARTH_RADIUS_METERS;
        return Math.hypot(dx, dy);
    }

    private static double distanceToSegment(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared == 0) {
            return Math.hypot(px - ax, py - ay);
        }
        double t = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / lengthSquared));
        return Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
    }
}
