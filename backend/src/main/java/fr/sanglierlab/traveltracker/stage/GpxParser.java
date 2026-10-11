package fr.sanglierlab.traveltracker.stage;

import fr.sanglierlab.traveltracker.track.PathSimplifier;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Lit un fichier GPX et en tire un tracé léger.
 * <ul>
 *   <li>Lecture en flux (StAX), sans bibliothèque : le fichier n'est jamais chargé en entier en mémoire.</li>
 *   <li>Protection contre les fichiers XML piégés : DTD et entités externes désactivées.</li>
 *   <li>Points du tracé ({@code trkpt}, toutes les pistes à la suite), sinon ceux d'une route ({@code rtept}) ;
 *       les waypoints isolés ne forment pas un tracé et sont ignorés.</li>
 *   <li>Le tracé est allégé : points à moins de 5 m supprimés, puis Douglas-Peucker à 50 m de tolérance (qui monte au besoin),
 *       pour tenir en {@link #MAX_POINTS} points : un trajet de plusieurs heures reste léger à afficher.</li>
 *   <li>Longitudes « continues » à la ligne de changement de date (un vol transpacifique reste d'un seul tenant).</li>
 * </ul>
 */
public final class GpxParser {

    public static final int MAX_POINTS = 1500;
    static final double MIN_STEP_METERS = 5;
    static final double TOLERANCE_METERS = 50;
    private static final double MAX_TOLERANCE_METERS = 20_000;
    /** Garde-fou : au-delà, le fichier est jugé abusif (un fichier de 10 Mo contient rarement plus de 300 000 points). */
    static final int MAX_POINTS_READ = 2_000_000;

    private GpxParser() {
    }

    public record Result(List<double[]> points, int originalCount) {
    }

    /** @throws IllegalArgumentException (message en français) si le fichier n'est pas un GPX exploitable */
    public static Result parse(InputStream in) {
        List<double[]> trackPoints = new ArrayList<>();
        List<double[]> routePoints = new ArrayList<>();
        try {
            XMLInputFactory factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            XMLStreamReader reader = factory.createXMLStreamReader(in);
            try {
                while (reader.hasNext()) {
                    if (reader.next() != XMLStreamConstants.START_ELEMENT) {
                        continue;
                    }
                    String element = reader.getLocalName();
                    List<double[]> target = element.equals("trkpt") ? trackPoints : element.equals("rtept") ? routePoints : null;
                    if (target == null) {
                        continue;
                    }
                    double[] point = point(reader.getAttributeValue(null, "lat"), reader.getAttributeValue(null, "lon"));
                    if (point != null) {
                        target.add(point);
                    }
                    if (trackPoints.size() + routePoints.size() > MAX_POINTS_READ) {
                        throw new IllegalArgumentException("Ce fichier GPX contient trop de points.");
                    }
                }
            } finally {
                reader.close();
            }
        } catch (XMLStreamException | RuntimeException e) {
            if (e instanceof IllegalArgumentException illegal) {
                throw illegal;
            }
            throw new IllegalArgumentException("Fichier GPX illisible : ce n'est pas un fichier GPX valide.", e);
        }

        List<double[]> raw = trackPoints.isEmpty() ? routePoints : trackPoints;
        if (raw.size() < 2) {
            throw new IllegalArgumentException("Ce fichier GPX ne contient aucun tracé (il faut au moins 2 points de piste ou de route).");
        }
        return new Result(lighten(continuous(raw)), raw.size());
    }

    private static double[] point(String latitude, String longitude) {
        if (latitude == null || longitude == null) {
            return null;
        }
        try {
            double lat = Double.parseDouble(latitude.strip());
            double lon = Double.parseDouble(longitude.strip());
            boolean valid = Math.abs(lat) <= 90 && Math.abs(lon) <= 180 && !Double.isNaN(lat) && !Double.isNaN(lon);
            return valid ? new double[]{lat, lon} : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Longitudes continues : 179,9 puis -179,9 devient 179,9 puis 180,1. */
    static List<double[]> continuous(List<double[]> points) {
        List<double[]> path = new ArrayList<>(points.size());
        double offset = 0;
        double previous = Double.NaN;
        for (double[] point : points) {
            if (!Double.isNaN(previous)) {
                double difference = point[1] - previous;
                if (difference > 180) {
                    offset -= 360;
                } else if (difference < -180) {
                    offset += 360;
                }
            }
            previous = point[1];
            path.add(new double[]{point[0], point[1] + offset});
        }
        return path;
    }

    static List<double[]> lighten(List<double[]> points) {
        List<double[]> thinned = PathSimplifier.thin(points, MIN_STEP_METERS);
        double tolerance = TOLERANCE_METERS;
        List<double[]> result = PathSimplifier.simplify(thinned, tolerance);
        while (result.size() > MAX_POINTS && tolerance < MAX_TOLERANCE_METERS) {
            tolerance *= 2;
            result = PathSimplifier.simplify(thinned, tolerance);
        }
        if (result.size() > MAX_POINTS) { // dernier recours : points régulièrement espacés, extrémités conservées
            List<double[]> sampled = new ArrayList<>(MAX_POINTS);
            for (int i = 0; i < MAX_POINTS; i++) {
                sampled.add(result.get((int) ((long) i * (result.size() - 1) / (MAX_POINTS - 1))));
            }
            result = sampled;
        }
        return result;
    }
}
