package fr.sanglierlab.traveltracker.track;

import fr.sanglierlab.traveltracker.track.TrackResponse.Segment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class TrackSegmenterTest {

    private static final LocalDateTime T0 = LocalDateTime.parse("2026-10-04T08:00:00");
    private static final Duration GAP = Duration.ofHours(6);

    private static TrackRow row(TrackSource source, Long tripId, double lat, double lon, LocalDateTime at) {
        return new TrackRow(source, tripId, BigDecimal.valueOf(lat), BigDecimal.valueOf(lon), at);
    }

    private static TrackRow device(double lat, double lon, LocalDateTime at) {
        return row(TrackSource.DEVICE, null, lat, lon, at);
    }

    /** Une ligne droite de 100 points, un par minute, à partir de l'heure donnée. */
    private static List<TrackRow> walk(LocalDateTime start, double lat0) {
        List<TrackRow> rows = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            rows.add(device(lat0 + i * 0.0001, 139.0, start.plusMinutes(i)));
        }
        return rows;
    }

    @Test
    void uneMarcheContinueFormeUneSeuleLigneAllegee() {
        TrackResponse response = TrackSegmenter.build(walk(T0, 35.0), GAP);

        assertThat(response.segments()).hasSize(1);
        Segment segment = response.segments().get(0);
        assertThat(segment.source()).isEqualTo(TrackSource.DEVICE);
        assertThat(segment.tripId()).isNull();
        assertThat(segment.points()).hasSize(2); // 100 points alignés -> les deux extrémités
        assertThat(segment.start()).isEqualTo(T0.toInstant(java.time.ZoneOffset.UTC));
        assertThat(segment.end()).isEqualTo(T0.plusMinutes(99).toInstant(java.time.ZoneOffset.UTC));
    }

    @Test
    void laLigneEstCoupeeQuandLEcartDepasseLeSeuil() {
        List<TrackRow> rows = new ArrayList<>(walk(T0, 35.0));
        rows.addAll(walk(T0.plusHours(10), 36.0)); // 10 h de trou

        TrackResponse response = TrackSegmenter.build(rows, GAP);

        assertThat(response.segments()).hasSize(2);
        assertThat(response.segments().get(0).points().get(0)[0]).isEqualTo(35.0);
        assertThat(response.segments().get(1).points().get(0)[0]).isEqualTo(36.0);
    }

    @Test
    void unEcartExactementEgalAuSeuilNEstPasCoupe() {
        List<TrackRow> rows = List.of(device(35.0, 139.0, T0), device(35.5, 139.0, T0.plusHours(6)));

        assertThat(TrackSegmenter.build(rows, GAP).segments()).hasSize(1);
        assertThat(TrackSegmenter.build(List.of(device(35.0, 139.0, T0), device(35.5, 139.0, T0.plusHours(6).plusSeconds(1))), GAP).segments()).hasSize(2);
    }

    @Test
    void unPointIsoleDonneUneLigneD_unSeulPoint() {
        List<TrackRow> rows = List.of(device(35.0, 139.0, T0), device(40.0, 140.0, T0.plusDays(2)));

        TrackResponse response = TrackSegmenter.build(rows, GAP);

        assertThat(response.segments()).hasSize(2);
        assertThat(response.segments()).allSatisfy(segment -> assertThat(segment.points()).hasSize(1));
    }

    @Test
    void unTrajetSuiviEstUneLigneUniqueSansCoupureEtSeparePourChaqueTrajet() {
        List<TrackRow> rows = new ArrayList<>();
        rows.add(row(TrackSource.ADSB, 7L, 35.5, 139.8, T0));
        rows.add(row(TrackSource.ADSB, 7L, 40.0, 150.0, T0.plusHours(12))); // 12 h d'écart : pas de coupure pour un trajet suivi
        rows.add(row(TrackSource.ADSB, 7L, 45.0, 170.0, T0.plusHours(14)));
        rows.add(row(TrackSource.AIS, 9L, 10.0, 20.0, T0.plusMinutes(5)));
        rows.add(row(TrackSource.AIS, 9L, 11.0, 21.0, T0.plusMinutes(10)));
        rows.sort(java.util.Comparator.comparing(TrackRow::recordedAt));

        TrackResponse response = TrackSegmenter.build(rows, GAP);

        assertThat(response.segments()).hasSize(2);
        Segment adsb = response.segments().get(0); // le plus ancien d'abord (début à T0, contre T0 + 5 min pour l'AIS)
        Segment ais = response.segments().get(1);
        // 3 points non alignés pour le vol : tous conservés ; sources et trajets distincts
        assertThat(ais.source()).isEqualTo(TrackSource.AIS);
        assertThat(ais.tripId()).isEqualTo(9L);
        assertThat(adsb.source()).isEqualTo(TrackSource.ADSB);
        assertThat(adsb.tripId()).isEqualTo(7L);
        assertThat(adsb.points()).hasSize(3);
    }

    @Test
    void lesLignesSontTrieesDuPlusAncienAuPlusRecent() {
        List<TrackRow> rows = new ArrayList<>(walk(T0, 35.0));
        rows.add(row(TrackSource.ADSB, 1L, 20.0, 30.0, T0.minusDays(1)));
        rows.sort(java.util.Comparator.comparing(TrackRow::recordedAt));

        List<Segment> segments = TrackSegmenter.build(rows, GAP).segments();

        assertThat(segments.get(0).source()).isEqualTo(TrackSource.ADSB);
        assertThat(segments.get(1).source()).isEqualTo(TrackSource.DEVICE);
    }

    @Test
    void laTraverseeDeLaLigneDeChangementDeDateResteUneLigneContinue() {
        List<TrackRow> rows = List.of(
                device(10.0, 179.0, T0),
                device(10.5, 179.9, T0.plusMinutes(10)),
                device(11.0, -179.9, T0.plusMinutes(20)), // de l'autre côté
                device(11.5, -179.0, T0.plusMinutes(30)));

        List<double[]> points = TrackSegmenter.build(rows, GAP).segments().get(0).points();

        // Tous les points se suivent sans saut de ~360° : on continue au-delà de 180
        for (int i = 1; i < points.size(); i++) {
            assertThat(Math.abs(points.get(i)[1] - points.get(i - 1)[1])).isLessThan(10);
        }
        assertThat(points.get(points.size() - 1)[1]).isEqualTo(181.0);
    }

    @Test
    void aucunPointDonneUneReponseVide() {
        TrackResponse response = TrackSegmenter.build(List.of(), GAP);

        assertThat(response.segments()).isEmpty();
        assertThat(response.last()).isNull();
    }

    @Test
    void laDernierePositionEstLePointLePlusRecentToutesSourcesConfondues() {
        List<TrackRow> rows = new ArrayList<>(walk(T0, 35.0));
        rows.add(row(TrackSource.ADSB, 3L, 48.0, 2.0, T0.plusDays(1)));
        rows.sort(java.util.Comparator.comparing(TrackRow::recordedAt));

        TrackResponse.LastPosition last = TrackSegmenter.build(rows, GAP).last();

        assertThat(last.source()).isEqualTo(TrackSource.ADSB);
        assertThat(last.latitude()).isEqualTo(48.0);
        assertThat(last.longitude()).isEqualTo(2.0);
    }

    @Test
    void laReponseResteLegereApresDesSemainesDeVoyage() {
        // 15 jours, 1 point par minute en marche aléatoire : ~21 000 points, des deux côtés d'un grand nombre de virages
        Random random = new Random(42);
        List<TrackRow> rows = new ArrayList<>();
        double lat = 35.0;
        double lon = 139.0;
        for (int i = 0; i < 15 * 24 * 60; i += 1) {
            lat += (random.nextDouble() - 0.5) * 0.0004;
            lon += (random.nextDouble() - 0.5) * 0.0004;
            if (i % 1440 < 600) { // le téléphone ne tourne que 10 h par jour : 15 lignes
                rows.add(device(lat, lon, T0.plusMinutes(i)));
            }
        }

        long start = System.nanoTime();
        TrackResponse response = TrackSegmenter.build(rows, GAP);
        long millis = (System.nanoTime() - start) / 1_000_000;

        int total = response.segments().stream().mapToInt(s -> s.points().size()).sum();
        assertThat(rows.size()).isGreaterThan(8_000);
        assertThat(total).isLessThanOrEqualTo(TrackSegmenter.MAX_POINTS);
        assertThat(response.segments()).hasSize(15);
        assertThat(millis).as("durée du calcul (ms)").isLessThan(3_000);
    }

    @Test
    void leBudgetDePointsEstRespecteMemeSiLaTraceEstTresChaotique() {
        // 60 000 points très dispersés : la tolérance doit augmenter jusqu'à rentrer dans le budget
        Random random = new Random(7);
        List<TrackRow> rows = new ArrayList<>();
        for (int i = 0; i < 60_000; i++) {
            rows.add(device(35.0 + random.nextDouble() * 0.5, 139.0 + random.nextDouble() * 0.5, T0.plusSeconds(i * 20L)));
        }

        TrackResponse response = TrackSegmenter.build(rows, Duration.ofDays(3));

        int total = response.segments().stream().mapToInt(s -> s.points().size()).sum();
        assertThat(total).isLessThanOrEqualTo(TrackSegmenter.MAX_POINTS);
        assertThat(total).isGreaterThan(100);
        // Même réduit à intervalles réguliers, le tracé garde ses extrémités
        double[] first = response.segments().get(0).points().get(0);
        double[] last = response.segments().get(0).points().get(response.segments().get(0).points().size() - 1);
        assertThat(first).containsExactly(rows.get(0).latitude().doubleValue(), rows.get(0).longitude().doubleValue());
        assertThat(last).containsExactly(rows.get(rows.size() - 1).latitude().doubleValue(), rows.get(rows.size() - 1).longitude().doubleValue());
    }
}
