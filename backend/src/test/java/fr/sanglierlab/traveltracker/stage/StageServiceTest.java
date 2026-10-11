package fr.sanglierlab.traveltracker.stage;

import fr.sanglierlab.traveltracker.common.ApiException;
import fr.sanglierlab.traveltracker.gallery.Direction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StageServiceTest {

    private static final LocalDate D1 = LocalDate.parse("2026-03-01");
    private static final LocalDate D5 = LocalDate.parse("2026-03-05");
    private static final LocalDate D9 = LocalDate.parse("2026-03-09");

    private StageRepository repository;
    private StageService service;

    @BeforeEach
    void setUp() {
        repository = mock(StageRepository.class);
        // Comme la base : l'enregistrement d'une nouvelle étape lui attribue un identifiant.
        when(repository.save(any())).thenAnswer(invocation -> {
            Stage saved = invocation.getArgument(0);
            return saved.getId() == null ? withId(saved, 100) : saved;
        });
        service = new StageService(repository);
    }

    private static Stage withId(Stage stage, long id) {
        try {
            Field field = Stage.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(stage, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return stage;
    }

    private static Stage stage(long id, int order, String name, StageKind kind, LocalDate start, LocalDate end) {
        Stage stage = new Stage(order);
        stage.apply(name, kind, start, end, new BigDecimal("35.68"), new BigDecimal("139.76"),
                kind.isTrip() ? new BigDecimal("34.66") : null, kind.isTrip() ? new BigDecimal("138.95") : null);
        return withId(stage, id);
    }

    private static StageRequest place(String name, LocalDate start, LocalDate end) {
        return new StageRequest(name, StageKind.PLACE, start, end, new BigDecimal("35.68"), new BigDecimal("139.76"), null, null);
    }

    private static StageRequest trip(StageKind kind) {
        return new StageRequest("Tokyo → Shimoda", kind, D5, D5, new BigDecimal("35.68"), new BigDecimal("139.76"),
                new BigDecimal("34.66"), new BigDecimal("138.95"));
    }

    private void existing(Stage... stages) {
        List<Stage> all = List.of(stages);
        when(repository.findAllByOrderBySortOrderAscIdAsc()).thenReturn(all);
        for (Stage stage : stages) {
            when(repository.findById(stage.getId())).thenReturn(Optional.of(stage));
        }
    }

    private static InputStream gpx() {
        String xml = "<gpx version=\"1.1\"><trk><trkseg>"
                + "<trkpt lat=\"35.0\" lon=\"139.0\"/><trkpt lat=\"35.0\" lon=\"139.011\"/><trkpt lat=\"35.009\" lon=\"139.011\"/>"
                + "</trkseg></trk></gpx>";
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }

    // --- Création / modification ---

    @Test
    void creeUnLieuFixeALaFinDeLaListe() {
        when(repository.maxSortOrder()).thenReturn(4);

        StageResponse response = service.create(place("  Tokyo ", D1, D5));

        ArgumentCaptor<Stage> saved = ArgumentCaptor.forClass(Stage.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getSortOrder()).isEqualTo(5);
        assertThat(saved.getValue().getName()).isEqualTo("Tokyo");
        assertThat(saved.getValue().getKind()).isEqualTo(StageKind.PLACE);
        assertThat(saved.getValue().getStartLatitude()).isEqualByComparingTo("35.68");
        assertThat(response.endLatitude()).isNull();
        assertThat(response.hasPath()).isFalse();
    }

    @Test
    void uneAutrePositionDeFinEstIgnoreeSurUnLieuFixe() {
        StageRequest request = new StageRequest("Tokyo", StageKind.PLACE, D1, D5, new BigDecimal("35.68"), new BigDecimal("139.76"),
                new BigDecimal("10"), new BigDecimal("10"));

        assertThat(service.create(request).endLatitude()).isNull();
    }

    @Test
    void unTrajetExigeUnePositionDeFin() {
        StageRequest withoutEnd = new StageRequest("Train", StageKind.TRAIN, D5, D5, new BigDecimal("35.68"), new BigDecimal("139.76"), null, null);
        StageRequest halfEnd = new StageRequest("Train", StageKind.TRAIN, D5, D5, new BigDecimal("35.68"), new BigDecimal("139.76"), new BigDecimal("34"), null);

        assertThatThrownBy(() -> service.create(withoutEnd)).isInstanceOf(ApiException.class).hasMessageContaining("position de fin");
        assertThatThrownBy(() -> service.create(halfEnd)).isInstanceOf(ApiException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void creeUnTrajetDeChaqueType() {
        for (StageKind kind : new StageKind[]{StageKind.TRAIN, StageKind.PLANE, StageKind.BOAT}) {
            assertThat(service.create(trip(kind)).kind()).isEqualTo(kind);
        }
    }

    @Test
    void refuseUneDateDeFinAvantLeDebutOuUnNomVide() {
        assertThatThrownBy(() -> service.create(place("Tokyo", D5, D1))).isInstanceOf(ApiException.class).hasMessageContaining("date de fin");
        assertThatThrownBy(() -> service.create(place("   ", D1, D5))).isInstanceOf(ApiException.class).hasMessageContaining("nom");
        assertThat(service.create(place("Une nuit", D5, D5)).startDate()).isEqualTo(D5); // même jour : valable
    }

    @Test
    void passerUnTrajetEnLieuFixeEffaceSaFinEtSonTrace() {
        Stage train = stage(7, 1, "Train", StageKind.TRAIN, D5, D5);
        train.setPathData("35.0,139.0;35.1,139.1");
        existing(train);

        StageResponse response = service.update(7, place("Shimoda", D5, D9));

        assertThat(response.kind()).isEqualTo(StageKind.PLACE);
        assertThat(response.endLatitude()).isNull();
        assertThat(response.hasPath()).isFalse();
    }

    @Test
    void modifierUnTrajetConserveSonTrace() {
        Stage train = stage(7, 1, "Train", StageKind.TRAIN, D5, D5);
        train.setPathData("35.0,139.0;35.1,139.1");
        existing(train);

        StageResponse response = service.update(7, trip(StageKind.TRAIN));

        assertThat(response.hasPath()).isTrue();
        assertThat(response.pathPoints()).isEqualTo(2);
    }

    @Test
    void uneEtapeInconnueDonneUneErreur404() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99)).isInstanceOf(ApiException.class).hasMessageContaining("introuvable");
        assertThatThrownBy(() -> service.delete(99)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.uploadGpx(99, gpx(), 10, false)).isInstanceOf(ApiException.class);
    }

    @Test
    void supprimeUneEtape() {
        Stage tokyo = stage(1, 1, "Tokyo", StageKind.PLACE, D1, D5);
        existing(tokyo);

        service.delete(1);

        verify(repository).delete(tokyo);
    }

    // --- Ordre ---

    @Test
    void monterEtDescendreEchangentLesVoisinesEtRenumerotent() {
        Stage a = stage(1, 1, "Tokyo", StageKind.PLACE, D1, D5);
        Stage b = stage(2, 2, "Train", StageKind.TRAIN, D5, D5);
        Stage c = stage(3, 3, "Shimoda", StageKind.PLACE, D5, D9);
        existing(a, b, c);

        service.move(2, Direction.UP);

        assertThat(a.getSortOrder()).isEqualTo(2);
        assertThat(b.getSortOrder()).isEqualTo(1);
        assertThat(c.getSortOrder()).isEqualTo(3);
    }

    @Test
    void ParAuxExtremitesDeplacerNeChangeRien() {
        Stage a = stage(1, 1, "Tokyo", StageKind.PLACE, D1, D5);
        Stage b = stage(2, 2, "Shimoda", StageKind.PLACE, D5, D9);
        existing(a, b);

        service.move(1, Direction.UP);
        service.move(2, Direction.DOWN);

        assertThat(a.getSortOrder()).isEqualTo(1);
        assertThat(b.getSortOrder()).isEqualTo(2);
    }

    @Test
    void deplacerRepareUnOrdreAvecDesTrous() {
        Stage a = stage(1, 10, "Tokyo", StageKind.PLACE, D1, D5);
        Stage b = stage(2, 10, "Shimoda", StageKind.PLACE, D5, D9); // même numéro : l'ordre est ambigu
        existing(a, b);

        service.move(2, Direction.DOWN);

        assertThat(a.getSortOrder()).isEqualTo(1);
        assertThat(b.getSortOrder()).isEqualTo(2);
    }

    @Test
    void trierParDateRecaleL_ordreEtConserveLOrdreActuelAuxDatesEgales() {
        Stage late = stage(1, 1, "Shimoda", StageKind.PLACE, D5, D9);
        Stage trainA = stage(2, 2, "Train A", StageKind.TRAIN, D5, D5);
        Stage first = stage(3, 3, "Tokyo", StageKind.PLACE, D1, D5);
        Stage trainB = stage(4, 4, "Train B", StageKind.TRAIN, D5, D5);
        existing(late, trainA, first, trainB);

        List<StageResponse> sorted = service.sortByDate();

        // Début le 1er, puis le 5 : fin le 5 avant fin le 9 ; Train A avant Train B (ordre actuel, à dates égales)
        assertThat(sorted).extracting(StageResponse::name).containsExactly("Tokyo", "Train A", "Train B", "Shimoda");
        assertThat(first.getSortOrder()).isEqualTo(1);
        assertThat(late.getSortOrder()).isEqualTo(4);
    }

    // --- GPX ---

    @Test
    void enregistreLeTraceGpxD_unTrajet() {
        Stage train = stage(7, 1, "Train", StageKind.TRAIN, D5, D5);
        existing(train);

        StageResponse response = service.uploadGpx(7, gpx(), 300, false);

        assertThat(response.hasPath()).isTrue();
        assertThat(response.pathPoints()).isEqualTo(3);
        assertThat(response.path()).hasSize(3);
        assertThat(response.startLatitude()).isEqualByComparingTo("35.68"); // positions inchangées
    }

    @Test
    void peutReprendreLesExtremitesDuTraceCommePositions() {
        Stage train = stage(7, 1, "Train", StageKind.TRAIN, D5, D5);
        existing(train);

        StageResponse response = service.uploadGpx(7, gpx(), 300, true);

        assertThat(response.startLatitude()).isEqualByComparingTo("35.0");
        assertThat(response.startLongitude()).isEqualByComparingTo("139.0");
        assertThat(response.endLatitude()).isEqualByComparingTo("35.009");
        assertThat(response.endLongitude()).isEqualByComparingTo("139.011");
    }

    @Test
    void refuseUnGpxPourUnLieuFixe() {
        existing(stage(1, 1, "Tokyo", StageKind.PLACE, D1, D5));

        assertThatThrownBy(() -> service.uploadGpx(1, gpx(), 300, false))
                .isInstanceOf(ApiException.class).hasMessageContaining("lieu fixe");
    }

    @Test
    void refuseUnGpxTropVolumineuxOuIllisible() {
        existing(stage(7, 1, "Train", StageKind.TRAIN, D5, D5));

        assertThatThrownBy(() -> service.uploadGpx(7, gpx(), 11L * 1024 * 1024, false))
                .isInstanceOf(ApiException.class).hasMessageContaining("10 Mo");
        assertThatThrownBy(() -> service.uploadGpx(7, new ByteArrayInputStream("pas du xml".getBytes(StandardCharsets.UTF_8)), 10, false))
                .isInstanceOf(ApiException.class).hasMessageContaining("illisible");
    }

    @Test
    void retireLeTrace() {
        Stage train = stage(7, 1, "Train", StageKind.TRAIN, D5, D5);
        train.setPathData("35.0,139.0;35.1,139.1");
        existing(train);

        assertThat(service.removeGpx(7).hasPath()).isFalse();
    }

    @Test
    void lesLongitudesContinuesRedeviennentValidesPourLesPositions() {
        assertThat(StageService.wrapLongitude(181.0)).isEqualTo(-179.0);
        assertThat(StageService.wrapLongitude(-181.0)).isEqualTo(179.0);
        assertThat(StageService.wrapLongitude(139.5)).isEqualTo(139.5);
    }

    // --- Listes ---

    @Test
    void laListeDAdministrationNInclutPasLesTracesMaisLItineairePublicOui() {
        Stage train = stage(7, 1, "Train", StageKind.TRAIN, D5, D5);
        train.setPathData("35.0,139.0;35.1,139.1;35.2,139.2");
        existing(train);

        StageResponse admin = service.list().get(0);
        StageResponse publicOne = service.publicItinerary().get(0);

        assertThat(admin.hasPath()).isTrue();
        assertThat(admin.pathPoints()).isEqualTo(3);
        assertThat(admin.path()).isNull();
        assertThat(publicOne.path()).hasSize(3);
        assertThat(new ArrayList<>(service.publicItinerary())).hasSize(1);
    }
}
