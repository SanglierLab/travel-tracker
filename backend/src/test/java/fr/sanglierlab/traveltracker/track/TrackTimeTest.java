package fr.sanglierlab.traveltracker.track;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrackTimeTest {

    private static final Instant NOW = Instant.parse("2026-10-04T12:00:00Z");

    @Test
    void lisUneDateIso8601AvecMillisecondes() {
        assertThat(TrackTime.parse("2026-10-04T10:00:00.123Z", null, NOW)).isEqualTo(Instant.parse("2026-10-04T10:00:00.123Z"));
    }

    @Test
    void lisUneDateAvecDecalageHoraire() {
        assertThat(TrackTime.parse("2026-10-04T12:00:00+02:00", null, NOW)).isEqualTo(Instant.parse("2026-10-04T10:00:00Z"));
    }

    @Test
    void lisUnHorodatageEnSecondesOuEnMillisecondes() {
        assertThat(TrackTime.parse(null, 1_790_000_000L, NOW)).isEqualTo(Instant.ofEpochSecond(1_790_000_000L));
        assertThat(TrackTime.parse(null, 1_790_000_000_123L, NOW)).isEqualTo(Instant.ofEpochMilli(1_790_000_000_123L));
    }

    @Test
    void laDateIsoPrimeSurLHorodatage() {
        assertThat(TrackTime.parse("2026-10-04T10:00:00Z", 1L, NOW)).isEqualTo(Instant.parse("2026-10-04T10:00:00Z"));
    }

    @Test
    void sansDateOnPrendLHeureDeReception() {
        assertThat(TrackTime.parse(null, null, NOW)).isEqualTo(NOW);
        assertThat(TrackTime.parse("  ", null, NOW)).isEqualTo(NOW);
    }

    @Test
    void refuseUneDateIllisibleOuUnHorodatageInvalide() {
        assertThatThrownBy(() -> TrackTime.parse("hier soir", null, NOW)).isInstanceOf(IllegalArgumentException.class);
        // Sans fuseau horaire : on ne devine pas
        assertThatThrownBy(() -> TrackTime.parse("2026-10-04T10:00:00", null, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TrackTime.parse(null, -5L, NOW)).isInstanceOf(IllegalArgumentException.class);
    }
}
