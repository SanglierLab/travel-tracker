package fr.sanglierlab.traveltracker.stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Stockage compact d'un tracé dans une colonne texte : « lat,lon;lat,lon;... » (6 décimales, soit environ 10 cm).
 * Volontairement sans JSON : une dépendance de moins, et le format reste lisible dans la base.
 */
public final class PathCodec {

    private PathCodec() {
    }

    public static String encode(List<double[]> points) {
        StringBuilder text = new StringBuilder(points.size() * 20);
        for (double[] point : points) {
            if (text.length() > 0) {
                text.append(';');
            }
            text.append(String.format(Locale.ROOT, "%.6f,%.6f", point[0], point[1]));
        }
        return text.toString();
    }

    /** @return les points [latitude, longitude] ; liste vide pour un texte vide ou null */
    public static List<double[]> decode(String text) {
        List<double[]> points = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return points;
        }
        for (String pair : text.split(";")) {
            int comma = pair.indexOf(',');
            points.add(new double[]{Double.parseDouble(pair.substring(0, comma)), Double.parseDouble(pair.substring(comma + 1))});
        }
        return points;
    }
}
