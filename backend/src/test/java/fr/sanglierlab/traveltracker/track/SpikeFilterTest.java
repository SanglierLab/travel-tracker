package fr.sanglierlab.traveltracker.track;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpikeFilterTest {

    private static final LocalDateTime T0 = LocalDateTime.parse("2026-10-04T08:00:00");
    private static final Instant LONG_AFTER = T0.plusDays(2).toInstant(ZoneOffset.UTC);
    /** Environ 50 km vers le nord. */
    private static final double FIFTY_KM = 0.45;

    private static TrackRow device(double lat, double lon, LocalDateTime at) {
        return new TrackRow(TrackSource.DEVICE, null, BigDecimal.valueOf(lat), BigDecimal.valueOf(lon), at);
    }

    /** Marche lente : un point toutes les 30 s, 11 m plus au nord à chaque fois. */
    private static TrackRow walk(int i) {
        return device(35.0 + i * 0.0001, 139.0, T0.plusSeconds(30L * i));
    }

    private static TrackRow spikeAt(int i) {
        return device(35.0 + FIFTY_KM, 139.0, T0.plusSeconds(30L * i));
    }

    private static List<TrackRow> walk(int from, int toExclusive) {
        List<TrackRow> rows = new ArrayList<>();
        for (int i = from; i < toExclusive; i++) {
            rows.add(walk(i));
        }
        return rows;
    }

    private static boolean contains(List<TrackRow> rows, double lat) {
        return rows.stream().anyMatch(row -> row.latitude().doubleValue() == lat);
    }

    @Test
    void unPointIsoleAPlusieursDizainesDeKilometresEstEcarte() {
        List<TrackRow> rows = walk(0, 10);
        rows.set(5, spikeAt(5));

        List<TrackRow> kept = SpikeFilter.apply(rows, LONG_AFTER);

        assertThat(kept.size()).isEqualTo(9);
        assertThat(contains(kept, 35.0 + FIFTY_KM)).isFalse();
    }

    @Test
    void deuxPicsConsecutifsSontEcartes() {
        List<TrackRow> rows = walk(0, 12);
        rows.set(5, spikeAt(5));
        rows.set(6, spikeAt(6));

        List<TrackRow> kept = SpikeFilter.apply(rows, LONG_AFTER);

        assertThat(kept.size()).isEqualTo(10);
        assertThat(contains(kept, 35.0 + FIFTY_KM)).isFalse();
    }

    @Test
    void unVraiDeplacementRapideEstConserve() {
        // le téléphone saute à 50 km et y reste : les points suivants confirment le nouveau lieu
        List<TrackRow> rows = walk(0, 5);
        for (int i = 5; i < 10; i++) {
            rows.add(device(35.0 + FIFTY_KM + (i - 5) * 0.0001, 139.0, T0.plusSeconds(30L * i)));
        }

        assertThat(SpikeFilter.apply(rows, LONG_AFTER).size()).isEqualTo(10);
    }

    @Test
    void apresUneLonguePauseLeDeplacementEstNormal() {
        // téléphone éteint pendant un vol : 800 km en 8 h = 100 km/h, rien de suspect
        List<TrackRow> rows = walk(0, 5);
        rows.add(device(42.0, 139.0, T0.plusHours(8)));
        rows.add(device(42.0001, 139.0, T0.plusHours(8).plusSeconds(30)));

        assertThat(SpikeFilter.apply(rows, LONG_AFTER).size()).isEqualTo(7);
    }

    @Test
    void leDernierPointSuspectRecentEstMasque() {
        List<TrackRow> rows = walk(0, 10);
        rows.add(spikeAt(10));
        Instant oneMinuteLater = T0.plusSeconds(30L * 10 + 60).toInstant(ZoneOffset.UTC);

        List<TrackRow> kept = SpikeFilter.apply(rows, oneMinuteLater);

        assertThat(kept.size()).isEqualTo(10);
        assertThat(contains(kept, 35.0 + FIFTY_KM)).isFalse();
    }

    @Test
    void leDernierPointSuspectNonConfirmeAuBoutDeTrenteMinutesEstConserve() {
        List<TrackRow> rows = walk(0, 10);
        rows.add(spikeAt(10));
        Instant later = T0.plusSeconds(30L * 10).plusMinutes(31).toInstant(ZoneOffset.UTC);

        assertThat(SpikeFilter.apply(rows, later).size()).isEqualTo(11);
    }

    @Test
    void unPicSuiviSeulementDUnBonPointEstDejaEcarte() {
        List<TrackRow> rows = walk(0, 9);
        rows.add(spikeAt(9));
        rows.add(walk(10));
        Instant now = T0.plusSeconds(30L * 10).toInstant(ZoneOffset.UTC);

        List<TrackRow> kept = SpikeFilter.apply(rows, now);

        assertThat(kept.size()).isEqualTo(10);
        assertThat(contains(kept, 35.0 + FIFTY_KM)).isFalse();
    }

    @Test
    void unPremierPointAberrantEstEcarte() {
        List<TrackRow> rows = walk(0, 8);
        rows.set(0, spikeAt(0));

        List<TrackRow> kept = SpikeFilter.apply(rows, LONG_AFTER);

        assertThat(kept.size()).isEqualTo(7);
        assertThat(kept.get(0).latitude().doubleValue()).isEqualTo(35.0001);
    }

    @Test
    void lesPointsDuTelephoneRattachesAUneSessionSontAussiFiltres() {
        List<TrackRow> rows = new ArrayList<>();
        for (TrackRow row : walk(0, 10)) {
            rows.add(new TrackRow(row.source(), 4L, row.latitude(), row.longitude(), row.recordedAt()));
        }
        TrackRow spike = spikeAt(5);
        rows.set(5, new TrackRow(spike.source(), 4L, spike.latitude(), spike.longitude(), spike.recordedAt()));

        assertThat(SpikeFilter.apply(rows, LONG_AFTER).size()).isEqualTo(9);
    }

    @Test
    void lesTrajetsSuivisNeSontJamaisFiltres() {
        // un avion va à 800 km/h : normal pour ADS-B (et ces points ne sont pas ceux du téléphone)
        List<TrackRow> rows = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            rows.add(new TrackRow(TrackSource.ADSB, 7L, BigDecimal.valueOf(35.0 + i * 0.1), BigDecimal.valueOf(139.0), T0.plusSeconds(30L * i)));
        }

        assertThat(SpikeFilter.apply(rows, LONG_AFTER).size()).isEqualTo(6);
    }

    @Test
    void lesAutresSourcesRestentEtLOrdreEstConserve() {
        List<TrackRow> rows = walk(0, 10);
        rows.set(5, spikeAt(5));
        TrackRow plane = new TrackRow(TrackSource.ADSB, 7L, BigDecimal.valueOf(48.0), BigDecimal.valueOf(2.0), T0.plusSeconds(30L * 5));
        rows.add(6, plane);

        List<TrackRow> kept = SpikeFilter.apply(rows, LONG_AFTER);

        assertThat(kept.size()).isEqualTo(10);
        assertThat(kept.get(5)).isEqualTo(plane);
    }

    @Test
    void uneMarcheNormaleNEstPasTouchee() {
        assertThat(SpikeFilter.apply(walk(0, 200), LONG_AFTER).size()).isEqualTo(200);
    }

    @Test
    void casLimites() {
        assertThat(SpikeFilter.apply(List.of(), LONG_AFTER).size()).isEqualTo(0);
        assertThat(SpikeFilter.apply(List.of(walk(0)), LONG_AFTER).size()).isEqualTo(1);
        assertThat(SpikeFilter.apply(walk(0, 2), LONG_AFTER).size()).isEqualTo(2);
    }
}
