package fr.sanglierlab.traveltracker.track;

import fr.sanglierlab.traveltracker.track.TrackPointFilter.Verdict;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TrackPointFilterTest {

    private static Verdict check(String lat, String lon, String accuracy) {
        return TrackPointFilter.check(new BigDecimal(lat), new BigDecimal(lon),
                accuracy == null ? null : new BigDecimal(accuracy), 100);
    }

    @Test
    void accepteUnPointPrecis() {
        assertThat(check("35.6852", "139.7528", "12.3")).isEqualTo(Verdict.ACCEPT);
    }

    @Test
    void accepteUnPointDontLaPrecisionEstInconnue() {
        assertThat(check("35.6852", "139.7528", null)).isEqualTo(Verdict.ACCEPT);
    }

    @Test
    void ecarteUnPointTropImprecisAvecUneLimiteInclusive() {
        assertThat(check("35.6852", "139.7528", "100.0")).isEqualTo(Verdict.ACCEPT);
        assertThat(check("35.6852", "139.7528", "100.1")).isEqualTo(Verdict.POOR_ACCURACY);
        assertThat(check("35.6852", "139.7528", "2500")).isEqualTo(Verdict.POOR_ACCURACY);
    }

    @Test
    void ecarteLaPositionNulleDesGpsSansFix() {
        assertThat(check("0", "0", null)).isEqualTo(Verdict.NO_FIX);
        assertThat(check("0.000000", "0.000000", "5.0")).isEqualTo(Verdict.NO_FIX);
    }

    @Test
    void unPointSurLEquateurOuLeMeridienDeGreenwichEstValable() {
        assertThat(check("0", "9.4", "10")).isEqualTo(Verdict.ACCEPT);
        assertThat(check("51.4778", "0", "10")).isEqualTo(Verdict.ACCEPT);
    }
}
