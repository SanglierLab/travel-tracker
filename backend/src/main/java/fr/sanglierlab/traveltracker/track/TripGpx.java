package fr.sanglierlab.traveltracker.track;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Export GPX 1.1 d'un trajet : un seul tracé, toutes les positions enregistrées (heures en UTC). */
public final class TripGpx {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT);

    private TripGpx() {
    }

    public static String build(String name, List<TrackPoint> points) {
        StringBuilder xml = new StringBuilder(128 + points.size() * 90);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<gpx version=\"1.1\" creator=\"travel-tracker\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n");
        xml.append("  <trk>\n    <name>").append(escape(name)).append("</name>\n    <trkseg>\n");
        for (TrackPoint point : points) {
            xml.append("      <trkpt lat=\"").append(point.getLatitude().toPlainString())
                    .append("\" lon=\"").append(point.getLongitude().toPlainString())
                    .append("\"><time>").append(TIME.format(point.getRecordedAt().atOffset(ZoneOffset.UTC))).append("</time></trkpt>\n");
        }
        xml.append("    </trkseg>\n  </trk>\n</gpx>\n");
        return xml.toString();
    }

    /** Nom de fichier sûr : « vol-AFR1234-2026-10-12.gpx », « livetrack-Tokyo-Asakusa-2026-10-12.gpx ». */
    public static String fileName(TrackedTrip trip) {
        String prefix = switch (trip.getType()) {
            case PLANE -> "vol";
            case BOAT -> "croisiere";
            case LIVETRACK -> "livetrack";
        };
        String name = trip.getType() == TripType.LIVETRACK ? trip.getLabel() : trip.getIdentifier();
        String safe = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (safe.isEmpty()) {
            safe = String.valueOf(trip.getId());
        }
        if (safe.length() > 60) {
            safe = safe.substring(0, 60);
        }
        return prefix + "-" + safe + "-" + trip.getScheduledDeparture().toLocalDate() + ".gpx";
    }

    private static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&apos;");
                default -> {
                    if (c >= 0x20 || c == '\n' || c == '\t') out.append(c); // caractères de contrôle interdits en XML
                }
            }
        }
        return out.toString();
    }
}
