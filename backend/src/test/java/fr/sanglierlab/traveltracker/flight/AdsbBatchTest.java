package fr.sanglierlab.traveltracker.flight;

import fr.sanglierlab.traveltracker.common.ApiException;
import fr.sanglierlab.traveltracker.flight.adsb.FlightPositionService;
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
    private FlightPositionService flightPositionService;
    private TaskScheduler scheduler;
    private ScheduledFuture<?> future;
    private AdsbBatch batch;

    @BeforeEach
    void setUp() {
        trips = mock(TrackedTripRepository.class);
        points = mock(TrackPointService.class);
        flightPositionService = mock(FlightPositionService.class);
        scheduler = mock(TaskScheduler.class);
        future = mock(ScheduledFuture.class);
        doReturn(future).when(scheduler).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));
        batch = new AdsbBatch(trips, points, flightPositionService, scheduler, INTERVAL);
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
