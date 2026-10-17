package fr.sanglierlab.traveltracker.track;

import fr.sanglierlab.traveltracker.common.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceSessionServiceTest {

    private static final LocalDateTime START = LocalDateTime.parse("2026-10-12T05:00:00");

    private TrackedTripRepository trips;
    private TrackPointRepository points;
    private DeviceSessionService service;

    @BeforeEach
    void setUp() {
        trips = mock(TrackedTripRepository.class);
        points = mock(TrackPointRepository.class);
        service = new DeviceSessionService(trips, points, Duration.ofHours(3));
        when(trips.save(any())).thenAnswer(invocation -> {
            TrackedTrip trip = invocation.getArgument(0);
            if (trip.getId() == null) {
                ReflectionTestUtils.setField(trip, "id", 99L);
            }
            return trip;
        });
    }

    private TrackedTrip session(long id, LocalDateTime start, String label) {
        TrackedTrip trip = new TrackedTrip(TripType.DEVICE, label, start, "device-" + start);
        trip.setStatus(TripStatus.FINISHED);
        ReflectionTestUtils.setField(trip, "id", id);
        when(trips.findById(id)).thenReturn(Optional.of(trip));
        return trip;
    }

    private void ownerAt(LocalDateTime at, TrackedTrip trip) {
        when(trips.findFirstByTypeAndScheduledDepartureLessThanEqualOrderByScheduledDepartureDescIdDesc(TripType.DEVICE, at))
                .thenReturn(Optional.ofNullable(trip));
    }

    @Test
    void lePremierPointOuvreUneSessionAPartirDeSonHeure() {
        ownerAt(START, null);

        assertThat(service.assign(START)).isEqualTo(99L);

        ArgumentCaptor<TrackedTrip> saved = ArgumentCaptor.forClass(TrackedTrip.class);
        verify(trips).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(TripType.DEVICE);
        assertThat(saved.getValue().getScheduledDeparture()).isEqualTo(START);
        assertThat(saved.getValue().getLabel()).isEmpty();
        assertThat(saved.getValue().getStatus()).isEqualTo(TripStatus.FINISHED);
    }

    @Test
    void unPointProcheDuDernierRejointLaSession() {
        TrackedTrip current = session(4, START, "");
        LocalDateTime last = START.plusHours(2);
        when(points.lastRecordedAt(4L)).thenReturn(last);
        LocalDateTime at = last.plusHours(3); // exactement le seuil : on reste dans la session
        ownerAt(at, current);

        assertThat(service.assign(at)).isEqualTo(4L);
        verify(trips, never()).save(any());
    }

    @Test
    void unSilenceDeTroisHeuresEtUneSecondeOuvreUneNouvelleSession() {
        TrackedTrip current = session(4, START, "");
        LocalDateTime last = START.plusHours(2);
        when(points.lastRecordedAt(4L)).thenReturn(last);
        LocalDateTime at = last.plusHours(3).plusSeconds(1);
        ownerAt(at, current);

        assertThat(service.assign(at)).isEqualTo(99L);
        ArgumentCaptor<TrackedTrip> saved = ArgumentCaptor.forClass(TrackedTrip.class);
        verify(trips).save(saved.capture());
        assertThat(saved.getValue().getScheduledDeparture()).isEqualTo(at);
    }

    @Test
    void uneExcursionDeNuitResteUneSeuleSession() {
        TrackedTrip current = session(4, LocalDateTime.parse("2026-10-12T21:00:00"), "");
        LocalDateTime last = LocalDateTime.parse("2026-10-12T23:59:30");
        when(points.lastRecordedAt(4L)).thenReturn(last);
        LocalDateTime afterMidnight = LocalDateTime.parse("2026-10-13T00:00:30");
        ownerAt(afterMidnight, current);

        assertThat(service.assign(afterMidnight)).isEqualTo(4L);
    }

    @Test
    void uneSessionSansPointRecoitLesPointsSuivants() {
        TrackedTrip cut = session(5, START.plusHours(1), "");
        when(points.lastRecordedAt(5L)).thenReturn(null);
        LocalDateTime at = START.plusHours(1).plusMinutes(10);
        ownerAt(at, cut);

        assertThat(service.assign(at)).isEqualTo(5L);
    }

    @Test
    void unPointEnRetardAvantUneCoupureNeRejointPasLaSessionSuivante() {
        // la coupure à 06:00 démarre la session 5 ; un point à 05:50 reste dans la session 4 qui l'a commencée à 05:00
        TrackedTrip before = session(4, START, "");
        when(points.lastRecordedAt(4L)).thenReturn(START.plusMinutes(45));
        LocalDateTime at = START.plusMinutes(50);
        ownerAt(at, before);

        assertThat(service.assign(at)).isEqualTo(4L);
    }

    @Test
    void couperDeplaceLesPointsSuivantsDansUneNouvelleSession() {
        TrackedTrip current = session(4, START, "Tokyo");
        LocalDateTime cut = START.plusHours(1);
        ownerAt(cut, current);

        DeviceSessionService.SessionResponse created = service.split(4, Instant.parse("2026-10-12T06:00:00Z"));

        assertThat(created.id()).isEqualTo(99L);
        assertThat(created.name()).isNull();
        verify(points).moveFrom(4L, 99L, cut);
        ArgumentCaptor<TrackedTrip> saved = ArgumentCaptor.forClass(TrackedTrip.class);
        verify(trips).save(saved.capture());
        assertThat(saved.getValue().getScheduledDeparture()).isEqualTo(cut);
    }

    @Test
    void refuseUneCoupureAvantLeDebutOuDansUneAutreSession() {
        TrackedTrip current = session(4, START, "");
        ownerAt(START.minusHours(1), null);
        ownerAt(START, current);
        TrackedTrip next = session(6, START.plusHours(8), "");
        ownerAt(START.plusHours(9), next);

        assertThatThrownBy(() -> service.split(4, Instant.parse("2026-10-12T04:00:00Z"))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.split(4, Instant.parse("2026-10-12T05:00:00Z"))).isInstanceOf(ApiException.class); // au début même
        assertThatThrownBy(() -> service.split(4, Instant.parse("2026-10-12T14:00:00Z"))).isInstanceOf(ApiException.class); // dans la session 6
        verify(points, never()).moveFrom(anyLong(), anyLong(), any());
    }

    @Test
    void renommerEtRetablirLeNomParDefaut() {
        TrackedTrip current = session(4, START, "");

        assertThat(service.rename(4, "  Tokyo – Asakusa ").name()).isEqualTo("Tokyo – Asakusa");
        assertThat(current.getLabel()).isEqualTo("Tokyo – Asakusa");
        assertThat(service.rename(4, "   ").name()).isNull();
        assertThatThrownBy(() -> service.rename(4, "x".repeat(201))).isInstanceOf(ApiException.class);
    }

    @Test
    void supprimerEnleveLaSessionEtSesPoints() {
        TrackedTrip current = session(4, START, "");

        service.delete(4);

        verify(points).deleteByTripId(4L);
        verify(trips).delete(current);
    }

    @Test
    void listeLesSessionsAvecLeurPlageDeTemps() {
        TrackedTrip current = session(4, START, "Tokyo");
        when(trips.findByTypeOrderByScheduledDepartureDescIdDesc(TripType.DEVICE)).thenReturn(List.of(current));
        when(points.tripRanges()).thenReturn(List.of(new TripPointRange(4L, 12L, START.plusMinutes(2), START.plusHours(1))));

        List<DeviceSessionService.SessionResponse> list = service.list();

        assertThat(list).hasSize(1);
        assertThat(list.get(0).pointCount()).isEqualTo(12);
        assertThat(list.get(0).startedAt()).isEqualTo(Instant.parse("2026-10-12T05:02:00Z"));
        assertThat(list.get(0).endedAt()).isEqualTo(Instant.parse("2026-10-12T06:00:00Z"));
        assertThat(list.get(0).name()).isEqualTo("Tokyo");
    }
}
