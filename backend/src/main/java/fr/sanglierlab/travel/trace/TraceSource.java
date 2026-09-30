package fr.sanglierlab.travel.trace;

/**
 * Origine d'une position.
 *
 * Chaque source est tracée différemment sur la carte — couleur et style de
 * ligne — de sorte qu'un trajet en avion se distingue au premier coup d'œil
 * d'un déplacement à pied. Les styles sont définis dans {@code theme.css},
 * via les variables {@code --trace-device}, {@code --trace-adsb} et
 * {@code --trace-ais}.
 */
public enum TraceSource {

    /** Téléphone du voyageur. */
    DEVICE("Téléphone"),

    /** Transpondeur d'aéronef. */
    ADSB("Vol"),

    /** Transpondeur de navire. */
    AIS("Navire");

    private final String label;

    TraceSource(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
