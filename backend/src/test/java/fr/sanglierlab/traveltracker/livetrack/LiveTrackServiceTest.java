package fr.sanglierlab.traveltracker.livetrack;

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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static fr.sanglierlab.traveltracker.livetrack.LiveTrackBatchTest.assignId;
import static fr.sanglierlab.traveltracker.livetrack.LiveTrackBatchTest.trip;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveTrackServiceTest {

    private static final String SESSION = "1debb03f-2acd-48b0-a8c9-2ba613bfcb3c";
    private static final String TOKEN = "5B72E4867D3AE6EB4B3D11F612044D8";
    private static final String LINK = "https://livetrack.garmin.com/session/" + SESSION + "/token/" + TOKEN;

    private TrackedTripRepository trips;
    private TrackPointRepository points;
    private LiveTrackBatch batch;
    private LiveTrackService service;

    @BeforeEach
    void setUp() {
        trips = mock(TrackedTripRepository.class);
        points = mock(TrackPointRepository.class);
        batch = mock(LiveTrackBatch.class);
        when(trips.save(any())).thenAnswer(invocation -> {
            TrackedTrip saved = invocation.getArgument(0);
            return saved.getId() == null ? assignId(saved, 100L) : saved;
        });
        when(points.tripStats()).thenReturn(List.of());
        service = new LiveTrackService(trips, points, batch);
    }

    private TrackedTrip existing(long id, TripStatus status) {
        TrackedTrip trip = trip(id, status);
        when(trips.findById(id)).thenReturn(Optional.of(trip));
        return trip;
    }

    @Test
    void enregistreUnLienSansLeDemarrerEtSansRenvoyerLeJeton() {
        LiveTrackResponse response = service.create(new LiveTrackRequest(LINK, null));

        ArgumentCaptor<TrackedTrip> saved = ArgumentCaptor.forClass(TrackedTrip.class);
        verify(trips).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(TripType.LIVETRACK);
        assertThat(saved.getValue().getIdentifier()).isEqualTo(SESSION);
        assertThat(saved.getValue().getAccessToken()).isEqualTo(TOKEN);
        assertThat(saved.getValue().getLabel()).isEqualTo("LiveTrack 1debb03f");
        assertThat(saved.getValue().getStatus()).isEqualTo(TripStatus.PLANNED);
        assertThat(response.status()).isEqualTo(TripStatus.PLANNED);
        assertThat(response.toString()).doesNotContain(TOKEN).doesNotContain(SESSION);
        verify(batch, never()).start(anyLong());
    }

    @Test
    void utiliseLeNomDonne() {
        service.create(new LiveTrackRequest("  " + LINK + " ", "  Rando du col  "));

        ArgumentCaptor<TrackedTrip> saved = ArgumentCaptor.forClass(TrackedTrip.class);
        verify(trips).save(saved.capture());
        assertThat(saved.getValue().getLabel()).isEqualTo("Rando du col");
    }

    @Test
    void refuseUnLienInvalide() {
        for (String invalid : new String[]{null, "", "n'importe quoi", "http://livetrack.garmin.com/session/" + SESSION + "/token/" + TOKEN,
                "https://example.com/session/" + SESSION + "/token/" + TOKEN}) {
            assertThatThrownBy(() -> service.create(new LiveTrackRequest(invalid, null)))
                    .as(String.valueOf(invalid)).isInstanceOf(ApiException.class).hasMessageContaining("Lien LiveTrack invalide");
        }
        verify(trips, never()).save(any());
    }

    @Test
    void refuseUneSessionDejaEnregistree() {
        when(trips.existsByTypeAndIdentifier(TripType.LIVETRACK, SESSION)).thenReturn(true);

        assertThatThrownBy(() -> service.create(new LiveTrackRequest(LINK, null)))
                .isInstanceOf(ApiException.class).hasMessageContaining("déjà enregistrée");
        verify(trips, never()).save(any());
    }

    @Test
    void demarrerPasseEnActivePuisLanceLeBatch() {
        existing(7, TripStatus.PLANNED);
        when(trips.findFirstByTypeAndStatus(TripType.LIVETRACK, TripStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThat(service.start(7).status()).isEqualTo(TripStatus.ACTIVE);

        InOrder order = inOrder(trips, batch);
        order.verify(trips).save(any());
        order.verify(batch).start(7);
    }

    @Test
    void refuseDeDemarrerSiUneAutreSessionEstSuivie() {
        existing(7, TripStatus.PLANNED);
        when(trips.findFirstByTypeAndStatus(TripType.LIVETRACK, TripStatus.ACTIVE)).thenReturn(Optional.of(trip(5, TripStatus.ACTIVE)));

        assertThatThrownBy(() -> service.start(7)).isInstanceOf(ApiException.class).hasMessageContaining("Arrêtez-le");
        verify(batch, never()).start(anyLong());
        verify(trips, never()).save(any());
    }

    @Test
    void redemarrerLaSessionDejaActiveEstPermis() {
        TrackedTrip trip = existing(7, TripStatus.ACTIVE);
        when(trips.findFirstByTypeAndStatus(TripType.LIVETRACK, TripStatus.ACTIVE)).thenReturn(Optional.of(trip));

        assertThat(service.start(7).status()).isEqualTo(TripStatus.ACTIVE);
        verify(batch).start(7);
    }

    @Test
    void arreterTermineLeSuiviEtConserveLesPositions() {
        existing(7, TripStatus.ACTIVE);

        assertThat(service.stop(7).status()).isEqualTo(TripStatus.FINISHED);
        verify(batch).stop(7);
        verify(points, never()).deleteByTripId(any());
    }

    @Test
    void arreterUneSessionNonSuivieNeChangeRien() {
        existing(7, TripStatus.PLANNED);

        assertThat(service.stop(7).status()).isEqualTo(TripStatus.PLANNED);
        verify(trips, never()).save(any());
    }

    @Test
    void uneSessionInconnueOuD_unAutreTypeDonne404() {
        when(trips.findById(99L)).thenReturn(Optional.empty());
        TrackedTrip flight = new TrackedTrip(TripType.PLANE, "Vol", LocalDateTime.parse("2026-10-15T10:00:00"), "AF123", null);
        when(trips.findById(98L)).thenReturn(Optional.of(assignId(flight, 98L)));

        for (long id : new long[]{99, 98}) {
            assertThatThrownBy(() -> service.start(id)).isInstanceOf(ApiException.class).hasMessageContaining("introuvable");
            assertThatThrownBy(() -> service.stop(id)).isInstanceOf(ApiException.class);
            assertThatThrownBy(() -> service.delete(id)).isInstanceOf(ApiException.class);
        }
    }

    @Test
    void supprimerEffaceLesPositionsPuisLaSession() {
        TrackedTrip trip = existing(7, TripStatus.FINISHED);

        service.delete(7);

        InOrder order = inOrder(points, trips);
        order.verify(points).deleteByTripId(7L);
        order.verify(trips).delete(trip);
    }

    @Test
    void refuseDeSupprimerUneSessionSuivie() {
        existing(7, TripStatus.ACTIVE);

        assertThatThrownBy(() -> service.delete(7)).isInstanceOf(ApiException.class).hasMessageContaining("arrêtez-le");
        verify(points, never()).deleteByTripId(any());
        verify(trips, never()).delete(any());
    }

    @Test
    void laListeIndiqueLesPositions() {
        TrackedTrip recent = trip(8, TripStatus.ACTIVE);
        TrackedTrip old = trip(7, TripStatus.FINISHED);
        when(trips.findByTypeOrderByScheduledDepartureDescIdDesc(TripType.LIVETRACK)).thenReturn(List.of(recent, old));
        when(points.tripStats()).thenReturn(List.of(new TripPointStats(7L, 42L, LocalDateTime.parse("2026-10-12T15:00:00"))));

        List<LiveTrackResponse> sessions = service.list();

        assertThat(sessions).extracting(LiveTrackResponse::id).containsExactly(8L, 7L);
        assertThat(sessions.get(0).pointCount()).isZero();
        assertThat(sessions.get(0).lastPointAt()).isNull();
        assertThat(sessions.get(1).pointCount()).isEqualTo(42);
        assertThat(sessions.get(1).lastPointAt()).isEqualTo(Instant.parse("2026-10-12T15:00:00Z"));
    }
}
