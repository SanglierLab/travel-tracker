package fr.sanglierlab.travel.trace;

import fr.sanglierlab.travel.common.BadRequestException;
import fr.sanglierlab.travel.common.NotFoundException;
import fr.sanglierlab.travel.trace.dto.IngestPointForm;
import fr.sanglierlab.travel.trace.dto.IngestResultDto;
import fr.sanglierlab.travel.trace.dto.TracePointDto;
import fr.sanglierlab.travel.trace.dto.TracePointUpdateForm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TraceServiceTest {

    @Autowired TraceService traces;

    private IngestPointForm point(Instant at, double lat, double lon) {
        return new IngestPointForm(TraceSource.DEVICE, at, lat, lon,
                null, null, null, null, null);
    }

    @Test
    @DisplayName("un lot de positions est enregistré")
    void storesBatch() {
        Instant base = Instant.parse("2026-10-10T08:00:00Z");

        IngestResultDto result = traces.ingest(List.of(
                point(base, 35.68, 139.75),
                point(base.plusSeconds(60), 35.69, 139.76)));

        assertThat(result.stored()).isEqualTo(2);
        assertThat(result.duplicates()).isZero();
    }

    @Test
    @DisplayName("réémettre le même lot ne crée pas de doublon")
    void ingestionIsIdempotent() {
        Instant base = Instant.parse("2026-10-10T08:00:00Z");
        List<IngestPointForm> batch = List.of(
                point(base, 35.68, 139.75),
                point(base.plusSeconds(60), 35.69, 139.76));

        traces.ingest(batch);
        IngestResultDto second = traces.ingest(batch);

        assertThat(second.stored()).isZero();
        assertThat(second.duplicates()).isEqualTo(2);
    }

    @Test
    @DisplayName("la source vaut DEVICE par défaut")
    void defaultsToDeviceSource() {
        traces.ingest(List.of(new IngestPointForm(
                null, Instant.parse("2026-10-10T08:00:00Z"), 35.68, 139.75,
                null, null, null, null, null)));

        var page = traces.listForAdmin(TraceSource.DEVICE, 0, 10);

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).source()).isEqualTo(TraceSource.DEVICE);
    }

    @Test
    @DisplayName("une longue interruption coupe le tracé en deux segments")
    void splitsSegmentsOnLongGap() {
        Instant base = Instant.parse("2026-10-10T08:00:00Z");

        traces.ingest(List.of(
                point(base, 35.68, 139.75),
                point(base.plusSeconds(300), 35.69, 139.76),
                // interruption de six heures : nuit à l'hôtel
                point(base.plus(6, ChronoUnit.HOURS), 35.90, 139.90),
                point(base.plus(6, ChronoUnit.HOURS).plusSeconds(300), 35.91, 139.91)));

        var map = traces.buildMap(null, null, null);

        assertThat(map.segments()).hasSize(2);
        assertThat(map.segments().get(0).coordinates()).hasSize(2);
        assertThat(map.segments().get(1).coordinates()).hasSize(2);
    }

    @Test
    @DisplayName("des relevés rapprochés forment un seul tracé")
    void keepsSingleSegmentWhenContinuous() {
        Instant base = Instant.parse("2026-10-10T08:00:00Z");

        traces.ingest(List.of(
                point(base, 35.68, 139.75),
                point(base.plusSeconds(120), 35.69, 139.76),
                point(base.plusSeconds(240), 35.70, 139.77)));

        var map = traces.buildMap(null, null, null);

        assertThat(map.segments()).hasSize(1);
        assertThat(map.segments().get(0).coordinates()).hasSize(3);
    }

    @Test
    @DisplayName("la dernière position connue est exposée")
    void exposesLastKnownPosition() {
        Instant base = Instant.parse("2026-10-10T08:00:00Z");

        traces.ingest(List.of(
                point(base, 35.68, 139.75),
                point(base.plusSeconds(600), 35.99, 139.99)));

        var map = traces.buildMap(null, null, null);

        assertThat(map.lastKnownPosition()).isNotNull();
        assertThat(map.lastKnownPosition().latitude()).isEqualTo(35.99);
    }

    @Test
    @DisplayName("un point mal placé peut être repositionné")
    void correctsMisplacedPoint() {
        traces.ingest(List.of(point(Instant.parse("2026-10-10T08:00:00Z"), 0.0, 0.0)));
        TracePointDto stored = traces.listForAdmin(null, 0, 10).content().get(0);

        TracePointDto corrected = traces.update(stored.id(),
                new TracePointUpdateForm(35.685, 139.752, null));

        assertThat(corrected.latitude()).isEqualTo(35.685);
        assertThat(corrected.longitude()).isEqualTo(139.752);
    }

    @Test
    @DisplayName("suppression d'une position")
    void deletesPoint() {
        traces.ingest(List.of(point(Instant.parse("2026-10-10T08:00:00Z"), 35.68, 139.75)));
        TracePointDto stored = traces.listForAdmin(null, 0, 10).content().get(0);

        traces.delete(stored.id());

        assertThat(traces.listForAdmin(null, 0, 10).content()).isEmpty();
    }

    @Test
    @DisplayName("suppression groupée")
    void deletesBatch() {
        Instant base = Instant.parse("2026-10-10T08:00:00Z");
        traces.ingest(List.of(
                point(base, 35.68, 139.75),
                point(base.plusSeconds(60), 35.69, 139.76),
                point(base.plusSeconds(120), 35.70, 139.77)));

        List<Long> ids = traces.listForAdmin(null, 0, 10).content()
                .stream().map(TracePointDto::id).limit(2).toList();

        assertThat(traces.deleteAll(ids)).isEqualTo(2);
        assertThat(traces.listForAdmin(null, 0, 10).content()).hasSize(1);
    }

    @Test
    @DisplayName("un envoi vide est refusé")
    void rejectsEmptyIngest() {
        assertThatThrownBy(() -> traces.ingest(List.of()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("position inconnue : erreur explicite")
    void unknownPointFails() {
        assertThatThrownBy(() -> traces.get(999_999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Position");
    }
}
