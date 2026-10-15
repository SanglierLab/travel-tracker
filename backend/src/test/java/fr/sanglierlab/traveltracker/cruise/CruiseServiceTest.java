package fr.sanglierlab.traveltracker.cruise;

import fr.sanglierlab.traveltracker.common.ApiException;
import fr.sanglierlab.traveltracker.track.TrackPointRepository;
import fr.sanglierlab.traveltracker.track.TrackedTrip;
import fr.sanglierlab.traveltracker.track.TrackedTripRepository;
import fr.sanglierlab.traveltracker.track.TripPointStats;
import fr.sanglierlab.traveltracker.track.TripStatus;
import fr.sanglierlab.traveltracker.track.TripType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static fr.sanglierlab.traveltracker.cruise.AisBatchTest.trip;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CruiseServiceTest {

    private TrackedTripRepository trips;
    private TrackPointRepository points;
    private AisBatch batch;
    private CruiseService service;

    @BeforeEach
    void setUp() {
        trips = mock(TrackedTripRepository.class);
        points = mock(TrackPointRepository.class);
        batch = mock(AisBatch.class);
        // Comme la base : l'enregistrement d'un nouvelle croisière lui attribue un identifiant.
        when(trips.save(any())).thenAnswer(invocation -> {
            TrackedTrip saved = invocation.getArgument(0);
            return saved.getId() == null ? AisBatchTest.assignId(saved, 100L) : saved;
        });
        when(points.tripStats()).thenReturn(List.of());
        service = new CruiseService(trips, points, batch);
    }

    private TrackedTrip existing(long id, String number, TripStatus status) {
        TrackedTrip trip = trip(id, number, status);
        when(trips.findById(id)).thenReturn(Optional.of(trip));
        return trip;
    }

    // --- Enregistrement ---

    @Test
    void enregistreUneCroisiereNormaliseeSansLaDemarrer() {
        CruiseResponse response = service.create(new CruiseRequest("227 123 456", LocalDate.parse("2026-10-12"), LocalTime.parse("14:30")));

        ArgumentCaptor<TrackedTrip> saved = ArgumentCaptor.forClass(TrackedTrip.class);
        verify(trips).save(saved.capture());
        assertThat(saved.getValue().getIdentifier()).isEqualTo("227123456");
        assertThat(saved.getValue().getLabel()).isEqualTo("Croisière 227123456");
        assertThat(saved.getValue().getType()).isEqualTo(TripType.BOAT);
        assertThat(saved.getValue().getStatus()).isEqualTo(TripStatus.PLANNED);
        assertThat(saved.getValue().getScheduledDeparture()).isEqualTo(LocalDateTime.parse("2026-10-12T14:30:00"));
        assertThat(response.status()).isEqualTo(TripStatus.PLANNED);
        verify(batch, never()).start(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void sansHeureLeDepartEstAMinuitUtc() {
        service.create(new CruiseRequest("228000111", LocalDate.parse("2026-10-12"), null));

        ArgumentCaptor<TrackedTrip> saved = ArgumentCaptor.forClass(TrackedTrip.class);
        verify(trips).save(saved.capture());
        assertThat(saved.getValue().getScheduledDeparture()).isEqualTo(LocalDateTime.parse("2026-10-12T00:00:00"));
    }

    @Test
    void refuseUnMmsiInvalide() {
        for (String invalid : new String[]{"12345678", "1234567890", "AFR123456", "22712345é", "227-123-456", "2 2 7"}) {
            assertThatThrownBy(() -> service.create(new CruiseRequest(invalid, LocalDate.parse("2026-10-12"), null)))
                    .as(invalid).isInstanceOf(ApiException.class).hasMessageContaining("MMSI invalide");
        }
        verify(trips, never()).save(any());
    }

    // --- Lancement / arrêt ---

    @Test
    void demarrerPasseLaCroisiereEnActivePuisLanceLeBatch() {
        existing(7, "227123456", TripStatus.PLANNED);
        when(trips.findFirstByTypeAndStatus(TripType.BOAT, TripStatus.ACTIVE)).thenReturn(Optional.empty());

        CruiseResponse response = service.start(7);

        assertThat(response.status()).isEqualTo(TripStatus.ACTIVE);
        // La base est à jour AVANT le lancement du batch : son premier cycle relit l'état de la croisière.
        InOrder order = inOrder(trips, batch);
        order.verify(trips).save(any());
        order.verify(batch).start(7);
    }

    @Test
    void refuseDeDemarrerUneCroisiereSiUneAutreEstDejaSuivie() {
        existing(7, "227123456", TripStatus.PLANNED);
        TrackedTrip other = trip(5, "228000111", TripStatus.ACTIVE);
        when(trips.findFirstByTypeAndStatus(TripType.BOAT, TripStatus.ACTIVE)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.start(7))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("228000111").hasMessageContaining("Arrêtez-le");
        verify(batch, never()).start(org.mockito.ArgumentMatchers.anyLong());
        verify(trips, never()).save(any());
    }

    @Test
    void redemarrerUneCroisiereDejaActiveEstPermis() {
        TrackedTrip trip = existing(7, "227123456", TripStatus.ACTIVE);
        when(trips.findFirstByTypeAndStatus(TripType.BOAT, TripStatus.ACTIVE)).thenReturn(Optional.of(trip));

        assertThat(service.start(7).status()).isEqualTo(TripStatus.ACTIVE);
        verify(batch).start(7);
    }

    @Test
    void onPeutRedemarrerUneCroisiereArretee() {
        existing(7, "227123456", TripStatus.FINISHED);
        when(trips.findFirstByTypeAndStatus(TripType.BOAT, TripStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThat(service.start(7).status()).isEqualTo(TripStatus.ACTIVE);
    }

    @Test
    void arreterTermineLeSuiviEtConserveLesPositions() {
        existing(7, "227123456", TripStatus.ACTIVE);

        CruiseResponse response = service.stop(7);

        assertThat(response.status()).isEqualTo(TripStatus.FINISHED);
        verify(batch).stop(7);
        verify(points, never()).deleteByTripId(any());
    }

    @Test
    void arreterUneCroisiereQuiNEstPasSuivieNeChangeRien() {
        existing(7, "227123456", TripStatus.PLANNED);

        assertThat(service.stop(7).status()).isEqualTo(TripStatus.PLANNED);
        verify(trips, never()).save(any());
    }

    @Test
    void uneCroisiereInconnuDonneUneErreur404() {
        when(trips.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.start(99)).isInstanceOf(ApiException.class).hasMessageContaining("introuvable");
        assertThatThrownBy(() -> service.stop(99)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.delete(99)).isInstanceOf(ApiException.class);
    }

    // --- Suppression ---

    @Test
    void supprimerEffaceLesPositionsPuisLaCroisiere() {
        TrackedTrip trip = existing(7, "227123456", TripStatus.FINISHED);

        service.delete(7);

        InOrder order = inOrder(points, trips);
        order.verify(points).deleteByTripId(7L);
        order.verify(trips).delete(trip);
    }

    @Test
    void refuseDeSupprimerUneCroisiereSuivie() {
        existing(7, "227123456", TripStatus.ACTIVE);

        assertThatThrownBy(() -> service.delete(7))
                .isInstanceOf(ApiException.class).hasMessageContaining("arrêtez-le");
        verify(points, never()).deleteByTripId(any());
        verify(trips, never()).delete(any());
    }

    // --- Liste ---

    @Test
    void laListeIndiqueLeNombreDePositionsEtLaDerniere() {
        TrackedTrip recent = trip(8, "228000111", TripStatus.ACTIVE);
        TrackedTrip old = trip(7, "227123456", TripStatus.FINISHED);
        when(trips.findByTypeOrderByScheduledDepartureDescIdDesc(TripType.BOAT)).thenReturn(List.of(recent, old));
        when(points.tripStats()).thenReturn(List.of(new TripPointStats(7L, 42L, LocalDateTime.parse("2026-10-12T15:00:00"))));

        List<CruiseResponse> cruises = service.list();

        assertThat(cruises).extracting(CruiseResponse::identifier).containsExactly("228000111", "227123456");
        assertThat(cruises.get(0).pointCount()).isZero();
        assertThat(cruises.get(0).lastPointAt()).isNull();
        assertThat(cruises.get(1).pointCount()).isEqualTo(42);
        assertThat(cruises.get(1).lastPointAt()).isEqualTo(Instant.parse("2026-10-12T15:00:00Z"));
    }
}
