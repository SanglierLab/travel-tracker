package fr.sanglierlab.traveltracker.flight;

import fr.sanglierlab.traveltracker.common.ApiException;
import fr.sanglierlab.traveltracker.track.TrackPointService;
import fr.sanglierlab.traveltracker.track.TrackSource;
import fr.sanglierlab.traveltracker.track.TrackedTrip;
import fr.sanglierlab.traveltracker.track.TrackedTripRepository;
import fr.sanglierlab.traveltracker.track.TripStatus;
import fr.sanglierlab.traveltracker.track.TripType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdsbBatchTest {

    private static final Duration INTERVAL = Duration.ofSeconds(30);

    private TrackedTripRepository trips;
    private TrackPointService points;
    private AdsbProvider provider;
    private TaskScheduler scheduler;
    private ScheduledFuture<?> future;
    private AdsbBatch batch;

    @BeforeEach
    void setUp() {
        trips = mock(TrackedTripRepository.class);
        points = mock(TrackPointService.class);
        provider = mock(AdsbProvider.class);
        scheduler = mock(TaskScheduler.class);
        future = mock(ScheduledFuture.class);
        doReturn(future).when(scheduler).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));
        batch = new AdsbBatch(trips, points, provider, scheduler, INTERVAL);
    }

    static TrackedTrip trip(long id, String flightNumber, TripStatus status) {
        TrackedTrip trip = new TrackedTrip(TripType.PLANE, "Vol " + flightNumber, LocalDateTime.parse("2026-10-12T00:00:00"), flightNumber);
        trip.setStatus(status);
        return assignId(trip, id);
    }

    /** Simule l'identifiant que la base attribue à l'enregistrement. */
    static TrackedTrip assignId(TrackedTrip trip, long id) {
        try {
            Field field = TrackedTrip.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(trip, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return trip;
    }

    @Test
    void demarrerProgrammeUnCycleRegulier() {
        batch.start(7);

        verify(scheduler).scheduleWithFixedDelay(any(Runnable.class), eq(INTERVAL));
        assertThat(batch.isRunning()).isTrue();
    }

    @Test
    void demarrerUnAutreVolRemplaceLeSuiviEnCours() {
        batch.start(7);
        batch.start(8);

        verify(future, times(1)).cancel(false);
        verify(scheduler, times(2)).scheduleWithFixedDelay(any(Runnable.class), eq(INTERVAL));
        assertThat(batch.isRunning()).isTrue();
    }

    @Test
    void arreterNeConcerneQueLeVolSuivi() {
        batch.start(7);

        batch.stop(8); // un autre vol : sans effet
        verify(future, never()).cancel(false);
        assertThat(batch.isRunning()).isTrue();

        batch.stop(7);
        verify(future).cancel(false);
        assertThat(batch.isRunning()).isFalse();
    }

    @Test
    void leCycleInterrogeLeFournisseurAvecLeNumeroDuVolActifEtEnregistreLesPositions() {
        when(trips.findById(7L)).thenReturn(Optional.of(trip(7, "AFR1234", TripStatus.ACTIVE)));
        Instant t1 = Instant.parse("2026-10-12T10:00:00Z");
        Instant t2 = Instant.parse("2026-10-12T10:00:30Z");
        when(provider.fetchPositions("AFR1234")).thenReturn(List.of(
                new AdsbPosition(49.0, 2.5, t1), new AdsbPosition(49.1, 2.9, t2)));

        batch.tick(7);

        verify(provider).fetchPositions("AFR1234");
        verify(points).ingest(eq(TrackSource.ADSB), eq(7L), eq(BigDecimal.valueOf(49.0)), eq(BigDecimal.valueOf(2.5)), isNull(), eq(t1));
        verify(points).ingest(eq(TrackSource.ADSB), eq(7L), eq(BigDecimal.valueOf(49.1)), eq(BigDecimal.valueOf(2.9)), isNull(), eq(t2));
    }

    @Test
    void leCycleSArreteQuandLeVolNEstPlusActif() {
        batch.start(7);
        when(trips.findById(7L)).thenReturn(Optional.of(trip(7, "AFR1234", TripStatus.FINISHED)));

        batch.tick(7);

        verify(provider, never()).fetchPositions(any());
        verify(future).cancel(false);
        assertThat(batch.isRunning()).isFalse();
    }

    @Test
    void leCycleSArreteQuandLeVolAEteSupprime() {
        batch.start(7);
        when(trips.findById(7L)).thenReturn(Optional.empty());

        batch.tick(7);

        verify(provider, never()).fetchPositions(any());
        assertThat(batch.isRunning()).isFalse();
    }

    @Test
    void unEchecDuFournisseurNeCasseRienEtLeCycleSuivantReprend() {
        when(trips.findById(7L)).thenReturn(Optional.of(trip(7, "AFR1234", TripStatus.ACTIVE)));
        Instant t = Instant.parse("2026-10-12T10:00:00Z");
        when(provider.fetchPositions("AFR1234"))
                .thenThrow(new IllegalStateException("fournisseur indisponible"))
                .thenReturn(List.of(new AdsbPosition(49.0, 2.5, t)));

        batch.tick(7); // échec : journalisé, aucune exception
        verify(points, never()).ingest(any(), any(), any(), any(), any(), any());

        batch.tick(7); // le cycle suivant reprend
        verify(points).ingest(eq(TrackSource.ADSB), eq(7L), any(), any(), isNull(), eq(t));
    }

    @Test
    void uneErreurALEnregistrementNeFaitPasPlanterLeCycle() {
        when(trips.findById(7L)).thenReturn(Optional.of(trip(7, "AFR1234", TripStatus.ACTIVE)));
        when(provider.fetchPositions("AFR1234")).thenReturn(List.of(new AdsbPosition(49.0, 2.5, Instant.parse("2026-10-12T10:00:00Z"))));
        doThrow(ApiException.badRequest("date dans le futur")).when(points).ingest(any(), any(), any(), any(), any(), any());

        batch.tick(7); // ne doit pas lever d'exception

        assertThat(batch.isRunning()).isFalse(); // aucun suivi n'avait été démarré : rien à annuler
    }

    @Test
    void repriseApresUnRedemarrageDuServeur() {
        when(trips.findFirstByTypeAndStatus(TripType.PLANE, TripStatus.ACTIVE)).thenReturn(Optional.of(trip(9, "JAL42", TripStatus.ACTIVE)));

        batch.resumeAfterRestart();

        verify(scheduler).scheduleWithFixedDelay(any(Runnable.class), eq(INTERVAL));
        assertThat(batch.isRunning()).isTrue();
    }

    @Test
    void aucuneRepriseSiAucunVolNEstActif() {
        when(trips.findFirstByTypeAndStatus(TripType.PLANE, TripStatus.ACTIVE)).thenReturn(Optional.empty());

        batch.resumeAfterRestart();

        verify(scheduler, never()).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));
        assertThat(batch.isRunning()).isFalse();
    }
}
