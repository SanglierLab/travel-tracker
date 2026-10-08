package fr.sanglierlab.traveltracker.flight;

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

import static fr.sanglierlab.traveltracker.flight.AdsbBatchTest.trip;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlightServiceTest {

    private TrackedTripRepository trips;
    private TrackPointRepository points;
    private AdsbBatch batch;
    private FlightService service;

    @BeforeEach
    void setUp() {
        trips = mock(TrackedTripRepository.class);
        points = mock(TrackPointRepository.class);
        batch = mock(AdsbBatch.class);
        // Comme la base : l'enregistrement d'un nouveau vol lui attribue un identifiant.
        when(trips.save(any())).thenAnswer(invocation -> {
            TrackedTrip saved = invocation.getArgument(0);
            return saved.getId() == null ? AdsbBatchTest.assignId(saved, 100L) : saved;
        });
        when(points.tripStats()).thenReturn(List.of());
        service = new FlightService(trips, points, batch);
    }

    private TrackedTrip existing(long id, String number, TripStatus status) {
        TrackedTrip trip = trip(id, number, status);
        when(trips.findById(id)).thenReturn(Optional.of(trip));
        return trip;
    }

    // --- Enregistrement ---

    @Test
    void enregistreUnVolNormaliseSansLeDemarrer() {
        FlightResponse response = service.create(new FlightRequest("afr 1234", LocalDate.parse("2026-10-12"), LocalTime.parse("14:30")));

        ArgumentCaptor<TrackedTrip> saved = ArgumentCaptor.forClass(TrackedTrip.class);
        verify(trips).save(saved.capture());
        assertThat(saved.getValue().getIdentifier()).isEqualTo("AFR1234");
        assertThat(saved.getValue().getLabel()).isEqualTo("Vol AFR1234");
        assertThat(saved.getValue().getType()).isEqualTo(TripType.PLANE);
        assertThat(saved.getValue().getStatus()).isEqualTo(TripStatus.PLANNED);
        assertThat(saved.getValue().getScheduledDeparture()).isEqualTo(LocalDateTime.parse("2026-10-12T14:30:00"));
        assertThat(response.status()).isEqualTo(TripStatus.PLANNED);
        verify(batch, never()).start(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void sansHeureLeDepartEstAMinuitUtc() {
        service.create(new FlightRequest("JAL42", LocalDate.parse("2026-10-12"), null));

        ArgumentCaptor<TrackedTrip> saved = ArgumentCaptor.forClass(TrackedTrip.class);
        verify(trips).save(saved.capture());
        assertThat(saved.getValue().getScheduledDeparture()).isEqualTo(LocalDateTime.parse("2026-10-12T00:00:00"));
    }

    @Test
    void refuseUnNumeroDeVolInvalide() {
        for (String invalid : new String[]{"A", "AFR-1234", "AFR12345678", "é", "AF R!"}) {
            assertThatThrownBy(() -> service.create(new FlightRequest(invalid, LocalDate.parse("2026-10-12"), null)))
                    .as(invalid).isInstanceOf(ApiException.class).hasMessageContaining("Numéro de vol invalide");
        }
        verify(trips, never()).save(any());
    }

    // --- Lancement / arrêt ---

    @Test
    void demarrerPasseLeVolEnActifPuisLanceLeBatch() {
        existing(7, "AFR1234", TripStatus.PLANNED);
        when(trips.findFirstByTypeAndStatus(TripType.PLANE, TripStatus.ACTIVE)).thenReturn(Optional.empty());

        FlightResponse response = service.start(7);

        assertThat(response.status()).isEqualTo(TripStatus.ACTIVE);
        // La base est à jour AVANT le lancement du batch : son premier cycle relit l'état du vol.
        InOrder order = inOrder(trips, batch);
        order.verify(trips).save(any());
        order.verify(batch).start(7);
    }

    @Test
    void refuseDeDemarrerUnVolSiUnAutreEstDejaSuivi() {
        existing(7, "AFR1234", TripStatus.PLANNED);
        TrackedTrip other = trip(5, "JAL42", TripStatus.ACTIVE);
        when(trips.findFirstByTypeAndStatus(TripType.PLANE, TripStatus.ACTIVE)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.start(7))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("JAL42").hasMessageContaining("Arrêtez-le");
        verify(batch, never()).start(org.mockito.ArgumentMatchers.anyLong());
        verify(trips, never()).save(any());
    }

    @Test
    void redemarrerUnVolDejaActifEstPermis() {
        TrackedTrip trip = existing(7, "AFR1234", TripStatus.ACTIVE);
        when(trips.findFirstByTypeAndStatus(TripType.PLANE, TripStatus.ACTIVE)).thenReturn(Optional.of(trip));

        assertThat(service.start(7).status()).isEqualTo(TripStatus.ACTIVE);
        verify(batch).start(7);
    }

    @Test
    void onPeutRedemarrerUnVolArrete() {
        existing(7, "AFR1234", TripStatus.FINISHED);
        when(trips.findFirstByTypeAndStatus(TripType.PLANE, TripStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThat(service.start(7).status()).isEqualTo(TripStatus.ACTIVE);
    }

    @Test
    void arreterTermineLeSuiviEtConserveLesPositions() {
        existing(7, "AFR1234", TripStatus.ACTIVE);

        FlightResponse response = service.stop(7);

        assertThat(response.status()).isEqualTo(TripStatus.FINISHED);
        verify(batch).stop(7);
        verify(points, never()).deleteByTripId(any());
    }

    @Test
    void arreterUnVolQuiNEstPasSuiviNeChangeRien() {
        existing(7, "AFR1234", TripStatus.PLANNED);

        assertThat(service.stop(7).status()).isEqualTo(TripStatus.PLANNED);
        verify(trips, never()).save(any());
    }

    @Test
    void unVolInconnuDonneUneErreur404() {
        when(trips.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.start(99)).isInstanceOf(ApiException.class).hasMessageContaining("introuvable");
        assertThatThrownBy(() -> service.stop(99)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.delete(99)).isInstanceOf(ApiException.class);
    }

    // --- Suppression ---

    @Test
    void supprimerEffaceLesPositionsPuisLeVol() {
        TrackedTrip trip = existing(7, "AFR1234", TripStatus.FINISHED);

        service.delete(7);

        InOrder order = inOrder(points, trips);
        order.verify(points).deleteByTripId(7L);
        order.verify(trips).delete(trip);
    }

    @Test
    void refuseDeSupprimerUnVolSuivi() {
        existing(7, "AFR1234", TripStatus.ACTIVE);

        assertThatThrownBy(() -> service.delete(7))
                .isInstanceOf(ApiException.class).hasMessageContaining("arrêtez-le");
        verify(points, never()).deleteByTripId(any());
        verify(trips, never()).delete(any());
    }

    // --- Liste ---

    @Test
    void laListeIndiqueLeNombreDePositionsEtLaDerniere() {
        TrackedTrip recent = trip(8, "JAL42", TripStatus.ACTIVE);
        TrackedTrip old = trip(7, "AFR1234", TripStatus.FINISHED);
        when(trips.findByTypeOrderByScheduledDepartureDescIdDesc(TripType.PLANE)).thenReturn(List.of(recent, old));
        when(points.tripStats()).thenReturn(List.of(new TripPointStats(7L, 42L, LocalDateTime.parse("2026-10-12T15:00:00"))));

        List<FlightResponse> flights = service.list();

        assertThat(flights).extracting(FlightResponse::identifier).containsExactly("JAL42", "AFR1234");
        assertThat(flights.get(0).pointCount()).isZero();
        assertThat(flights.get(0).lastPointAt()).isNull();
        assertThat(flights.get(1).pointCount()).isEqualTo(42);
        assertThat(flights.get(1).lastPointAt()).isEqualTo(Instant.parse("2026-10-12T15:00:00Z"));
    }
}
