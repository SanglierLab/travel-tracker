package fr.sanglierlab.traveltracker.track;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TrackAccuracyTest {

    @Test
    void lisUnePrecisionEnMetresAUneDecimale() {
        assertThat(TrackAccuracy.parse("12.34")).isEqualTo(new BigDecimal("12.3"));
        assertThat(TrackAccuracy.parse("25")).isEqualTo(new BigDecimal("25.0"));
        assertThat(TrackAccuracy.parse("  8.0 ")).isEqualTo(new BigDecimal("8.0"));
    }

    @Test
    void accepteLaVirguleDecimale() {
        assertThat(TrackAccuracy.parse("12,5")).isEqualTo(new BigDecimal("12.5"));
    }

    @Test
    void uneValeurAbsenteVideNulleOuIllisibleVautInconnue() {
        // GPSLogger peut envoyer une valeur vide : « inconnue », jamais « parfaite »
        assertThat(TrackAccuracy.parse(null)).isNull();
        assertThat(TrackAccuracy.parse("")).isNull();
        assertThat(TrackAccuracy.parse("   ")).isNull();
        assertThat(TrackAccuracy.parse("0")).isNull();
        assertThat(TrackAccuracy.parse("0.0")).isNull();
        assertThat(TrackAccuracy.parse("-3")).isNull();
        assertThat(TrackAccuracy.parse("abc")).isNull();
        assertThat(TrackAccuracy.parse("12.3.4")).isNull();
    }
}
