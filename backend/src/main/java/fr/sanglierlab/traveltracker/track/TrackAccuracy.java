package fr.sanglierlab.traveltracker.track;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Lit la précision envoyée par GPSLogger ({@code %ACC}, en mètres).
 * GPSLogger peut envoyer une valeur vide ou 0 quand il ne connaît pas la précision : c'est alors « inconnue » (null),
 * jamais « parfaite ». Une virgule décimale est acceptée.
 */
public final class TrackAccuracy {

    private TrackAccuracy() {
    }

    /** @return la précision en mètres (une décimale), ou null si absente, illisible ou nulle */
    public static BigDecimal parse(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.strip().replace(',', '.');
        if (text.isEmpty()) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(text).setScale(1, RoundingMode.HALF_UP);
            return value.signum() > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
