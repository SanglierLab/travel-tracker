package fr.sanglierlab.traveltracker.track;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Filtre de « pics » appliqué à l'affichage (rien n'est supprimé en base) : un téléphone pose parfois un point à des dizaines
 * de kilomètres de la réalité, puis reprend un bon fix, ce qui tracerait une aiguille sur la carte.
 * <p>
 * Ne concerne que les points du téléphone (source {@code DEVICE}, sans trajet) : ADS-B et AIS sont considérés fiables.
 * Un point est <b>suspect</b> quand la vitesse implicite depuis le dernier bon point dépasse {@link #MAX_SPEED_KMH}. Il est alors
 * <b>écarté</b> si au moins {@link #CONFIRMATIONS} des {@link #LOOKAHEAD} points suivants sont plus proches du dernier bon point que de lui
 * (le téléphone est « revenu »), sinon conservé : c'est un vrai déplacement (téléphone éteint pendant un vol, par exemple).
 * <p>
 * Tant que les points suivants manquent pour trancher (le point est le dernier ou l'avant-dernier du direct), un point suspect est
 * <b>masqué</b> pendant {@link #MAX_HOLD} au plus, puis conservé.
 */
public final class SpikeFilter {

    /** Vitesse implicite au-delà de laquelle un point est suspect (un avion de ligne est suivi par ADS-B, pas par le téléphone). */
    static final double MAX_SPEED_KMH = 350;
    /** Nombre de points suivants examinés pour trancher. */
    static final int LOOKAHEAD = 3;
    /** Nombre de ces points qui doivent « revenir » près du dernier bon point pour écarter le suspect. */
    static final int CONFIRMATIONS = 2;
    /** Durée maximale pendant laquelle le dernier point du direct reste masqué faute de confirmation. */
    static final Duration MAX_HOLD = Duration.ofMinutes(30);

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private SpikeFilter() {
    }

    /**
     * @param rows tous les points, triés par heure croissante
     * @param now  heure courante (pour le délai de masquage du dernier point)
     * @return les mêmes points, dans le même ordre, sans les pics du téléphone
     */
    public static List<TrackRow> apply(List<TrackRow> rows, Instant now) {
        List<Integer> device = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            if (isDevice(rows.get(i))) {
                device.add(i);
            }
        }
        boolean[] dropped = new boolean[rows.size()];
        TrackRow good = null;
        for (int d = 0; d < device.size(); d++) {
            TrackRow candidate = rows.get(device.get(d));
            if (good == null) {
                if (firstPointIsSpike(rows, device, d)) {
                    dropped[device.get(d)] = true;
                } else {
                    good = candidate;
                }
                continue;
            }
            if (speedKmh(good, candidate) <= MAX_SPEED_KMH) {
                good = candidate;
                continue;
            }
            switch (verdict(rows, device, d, good, now)) {
                case KEEP -> good = candidate;
                case DROP, HIDE -> dropped[device.get(d)] = true;
            }
        }
        List<TrackRow> kept = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            if (!dropped[i]) {
                kept.add(rows.get(i));
            }
        }
        return kept;
    }

    private enum Verdict {KEEP, DROP, HIDE}

    private static Verdict verdict(List<TrackRow> rows, List<Integer> device, int index, TrackRow good, Instant now) {
        TrackRow candidate = rows.get(device.get(index));
        int following = Math.min(LOOKAHEAD, device.size() - index - 1);
        int back = 0;
        for (int k = 1; k <= following; k++) {
            TrackRow next = rows.get(device.get(index + k));
            if (distanceMeters(next, good) < distanceMeters(next, candidate)) {
                back++;
            }
        }
        if (back >= CONFIRMATIONS) {
            return Verdict.DROP;
        }
        boolean undecided = following < LOOKAHEAD && back + (LOOKAHEAD - following) >= CONFIRMATIONS;
        if (undecided && Duration.between(candidate.instant(), now).compareTo(MAX_HOLD) <= 0) {
            return Verdict.HIDE;
        }
        return Verdict.KEEP;
    }

    /** Le premier point n'a pas de « bon point » de référence : il est un pic si le suivant est inatteignable mais cohérent avec le troisième. */
    private static boolean firstPointIsSpike(List<TrackRow> rows, List<Integer> device, int index) {
        if (index != 0 || device.size() < 3) {
            return false;
        }
        TrackRow first = rows.get(device.get(0));
        TrackRow second = rows.get(device.get(1));
        TrackRow third = rows.get(device.get(2));
        return speedKmh(first, second) > MAX_SPEED_KMH && speedKmh(second, third) <= MAX_SPEED_KMH;
    }

    private static boolean isDevice(TrackRow row) {
        return row.source() == TrackSource.DEVICE;
    }

    private static double speedKmh(TrackRow from, TrackRow to) {
        double seconds = Math.max(1, Math.abs(Duration.between(from.instant(), to.instant()).toMillis()) / 1000.0);
        return distanceMeters(from, to) / 1000.0 / (seconds / 3600.0);
    }

    private static double distanceMeters(TrackRow a, TrackRow b) {
        double lat1 = Math.toRadians(a.latitude().doubleValue());
        double lat2 = Math.toRadians(b.latitude().doubleValue());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b.longitude().doubleValue() - a.longitude().doubleValue());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1, Math.sqrt(h)));
    }
}
