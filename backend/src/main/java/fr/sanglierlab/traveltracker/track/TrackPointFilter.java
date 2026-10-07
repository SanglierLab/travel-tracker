package fr.sanglierlab.traveltracker.track;

import java.math.BigDecimal;

/**
 * Règles « sans mémoire » appliquées à un point du téléphone au moment de l'ajout : elles ne dépendent que du point lui-même.
 * (Les erreurs qui ne se voient qu'avec les points voisins, comme un point isolé à 50 km, sont traitées à l'affichage du tracé.)
 */
public final class TrackPointFilter {

    public enum Verdict {
        ACCEPT, NO_FIX, POOR_ACCURACY
    }

    private TrackPointFilter() {
    }

    /**
     * @param accuracyMeters précision annoncée, ou null si inconnue (le point est alors accepté)
     * @param maxAccuracyMeters au-delà, le point est jugé trop imprécis
     */
    public static Verdict check(BigDecimal latitude, BigDecimal longitude, BigDecimal accuracyMeters, int maxAccuracyMeters) {
        // 0° / 0° : valeur par défaut d'un GPS qui n'a pas (encore) de position, au milieu de l'océan Atlantique.
        if (latitude.signum() == 0 && longitude.signum() == 0) {
            return Verdict.NO_FIX;
        }
        if (accuracyMeters != null && accuracyMeters.compareTo(BigDecimal.valueOf(maxAccuracyMeters)) > 0) {
            return Verdict.POOR_ACCURACY;
        }
        return Verdict.ACCEPT;
    }
}
