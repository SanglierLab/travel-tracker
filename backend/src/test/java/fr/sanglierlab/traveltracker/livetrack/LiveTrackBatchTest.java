package fr.sanglierlab.traveltracker.livetrack;

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

class LiveTrackBatchTest {

    private static final Duration INTERVAL = Duration.ofSeconds(60);
    private static final String SESSION = "1debb03f-2acd-48b0-a8c9-2ba613bfcb3c";
    private static final String TOKEN = "5B72E4867D3AE6EB4B3D11F612044D8";
    private static final Instant T1 = Instant.parse("2026-10-15T18:00:00Z");
    private static final Instant T2 = Instant.parse("2026-10-15T18:00:10Z");
    private static final Instant T3 = Instant.parse("2026-10-15T18:00:20Z");

    private TrackedTripRepository trips;
    private TrackPointService points;
    private LiveTrackProvider provider;
    private TaskScheduler scheduler;
    private ScheduledFuture<?> future;
    private LiveTrackBatch batch;

    @BeforeEach
    void setUp() {
        trips = mock(TrackedTripRepository.class);
        points = mock(TrackPointService.class);
        provider = mock(LiveTrackProvider.class);
        scheduler = mock(TaskScheduler.class);
        future = mock(ScheduledFuture.class);
        doReturn(future).when(scheduler).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));
        batch = new LiveTrackBatch(trips, points, provider, scheduler, INTERVAL);
    }

    static TrackedTrip trip(long id, TripStatus status) {
        TrackedTrip trip = new TrackedTrip(TripType.LIVETRACK, "LiveTrack 1debb03f", LocalDateTime.parse("2026-10-15T18:00:00"), SESSION, TOKEN);
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

    private static LiveTrackPoint point(double lat, double lon, Instant at) {
        return new LiveTrackPoint(lat, lon, at);
    }

    @Test
    void demarrerProgrammeUnCycleRegulier() {
        batch.start(7);

        verify(scheduler).scheduleWithFixedDelay(any(Runnable.class), eq(INTERVAL));
        assertThat(batch.isRunning()).isTrue();
    }

    @Test
    void demarrerUneAutreSessionRemplaceLeSuiviEnCours() {
        batch.start(7);
        batch.start(8);

        verify(future, times(1)).cancel(false);
        verify(scheduler, times(2)).scheduleWithFixedDelay(any(Runnable.class), eq(INTERVAL));
    }

    @Test
    void arreterNeConcerneQueLaSessionSuivie() {
        batch.start(7);

        batch.stop(8);
        verify(future, never()).cancel(false);
        assertThat(batch.isRunning()).isTrue();

        batch.stop(7);
        verify(future).cancel(false);
        assertThat(batch.isRunning()).isFalse();
    }

    @Test
    void lePremierCycleDemandeLHistoriqueEtEnregistreTousLesPointsCommeGarmin() {
        when(trips.findById(7L)).thenReturn(Optional.of(trip(7, TripStatus.ACTIVE)));
        when(provider.fetch(SESSION, TOKEN, null)).thenReturn(new LiveTrackUpdate(
                List.of(point(48.1, 2.1, T1), point(48.2, 2.2, T2)), false));
        when(points.ingest(any(), any(), any(), any(), any(), any())).thenReturn(TrackPointService.Result.CREATED);

        batch.tick(7);

        verify(provider).fetch(SESSION, TOKEN, null);
        verify(points).ingest(eq(TrackSource.GARMIN), eq(7L), eq(BigDecimal.valueOf(48.1)), eq(BigDecimal.valueOf(2.1)), isNull(), eq(T1));
        verify(points).ingest(eq(TrackSource.GARMIN), eq(7L), eq(BigDecimal.valueOf(48.2)), eq(BigDecimal.valueOf(2.2)), isNull(), eq(T2));
    }

    @Test
    void leCycleSuivantNeDemandeQueLeNouveau() {
        when(trips.findById(7L)).thenReturn(Optional.of(trip(7, TripStatus.ACTIVE)));
        when(provider.fetch(SESSION, TOKEN, null)).thenReturn(new LiveTrackUpdate(List.of(point(48.1, 2.1, T1), point(48.2, 2.2, T2)), false));
        when(provider.fetch(SESSION, TOKEN, T2)).thenReturn(new LiveTrackUpdate(List.of(point(48.3, 2.3, T3)), false));

        batch.tick(7);
        batch.tick(7);

        verify(provider).fetch(SESSION, TOKEN, null);
        verify(provider).fetch(SESSION, TOKEN, T2); // l'heure du dernier point reçu
        verify(points, times(3)).ingest(eq(TrackSource.GARMIN), eq(7L), any(), any(), isNull(), any());
    }

    @Test
    void rienDeNouveauNEstPasUneErreur() {
        when(trips.findById(7L)).thenReturn(Optional.of(trip(7, TripStatus.ACTIVE)));
        when(provider.fetch(SESSION, TOKEN, null)).thenReturn(new LiveTrackUpdate(List.of(), false));

        batch.tick(7);

        verify(points, never()).ingest(any(), any(), any(), any(), any(), any());
        verify(trips, never()).save(any());
    }

    @Test
    void unPointRefuseNeBloquePasLesSuivants() {
        when(trips.findById(7L)).thenReturn(Optional.of(trip(7, TripStatus.ACTIVE)));
        when(provider.fetch(SESSION, TOKEN, null)).thenReturn(new LiveTrackUpdate(
                List.of(point(48.1, 2.1, T1), point(48.2, 2.2, T2), point(48.3, 2.3, T3)), false));
        doThrow(ApiException.badRequest("date dans le futur")).when(points)
                .ingest(any(), any(), eq(BigDecimal.valueOf(48.2)), any(), any(), any());

        batch.tick(7);

        verify(points, times(3)).ingest(eq(TrackSource.GARMIN), eq(7L), any(), any(), isNull(), any());
    }

    @Test
    void unEchecDeGarminNeCasseRienEtLeCycleSuivantReprendAuMemePoint() {
        when(trips.findById(7L)).thenReturn(Optional.of(trip(7, TripStatus.ACTIVE)));
        when(provider.fetch(SESSION, TOKEN, null))
                .thenThrow(new LiveTrackException("Garmin a répondu HTTP 503"))
                .thenReturn(new LiveTrackUpdate(List.of(point(48.1, 2.1, T1)), false));

        batch.tick(7); // échec : journalisé, aucune exception, rien d'enregistré
        verify(points, never()).ingest(any(), any(), any(), any(), any(), any());

        batch.tick(7); // le cycle suivant redemande depuis le même point (aucun point n'avait été reçu)
        verify(points).ingest(eq(TrackSource.GARMIN), eq(7L), any(), any(), isNull(), eq(T1));
        verify(provider, times(2)).fetch(SESSION, TOKEN, null);
    }

    @Test
    void laFinDeSessionTermineLeSuivi() {
        batch.start(7);
        TrackedTrip trip = trip(7, TripStatus.ACTIVE);
        when(trips.findById(7L)).thenReturn(Optional.of(trip));
        when(provider.fetch(SESSION, TOKEN, null)).thenReturn(new LiveTrackUpdate(List.of(point(48.1, 2.1, T1)), true));

        batch.tick(7);

        verify(points).ingest(eq(TrackSource.GARMIN), eq(7L), any(), any(), isNull(), eq(T1)); // le dernier point est gardé
        assertThat(trip.getStatus()).isEqualTo(TripStatus.FINISHED);
        verify(trips).save(trip);
        verify(future).cancel(false);
        assertThat(batch.isRunning()).isFalse();
    }

    @Test
    void uneSessionDisparueChezGarminTermineLeSuivi() {
        batch.start(7);
        TrackedTrip trip = trip(7, TripStatus.ACTIVE);
        when(trips.findById(7L)).thenReturn(Optional.of(trip));
        when(provider.fetch(SESSION, TOKEN, null)).thenThrow(new LiveTrackGoneException("la session LiveTrack n'existe plus (HTTP 404)"));

        batch.tick(7);

        assertThat(trip.getStatus()).isEqualTo(TripStatus.FINISHED);
        verify(trips).save(trip);
        assertThat(batch.isRunning()).isFalse();
    }

    @Test
    void leCycleSArreteQuandLaSessionNEstPlusActive() {
        batch.start(7);
        when(trips.findById(7L)).thenReturn(Optional.of(trip(7, TripStatus.FINISHED)));

        batch.tick(7);

        verify(provider, never()).fetch(any(), any(), any());
        verify(future).cancel(false);
        assertThat(batch.isRunning()).isFalse();
    }

    @Test
    void leCycleSArreteQuandLaSessionAEteSupprimee() {
        batch.start(7);
        when(trips.findById(7L)).thenReturn(Optional.empty());

        batch.tick(7);

        verify(provider, never()).fetch(any(), any(), any());
        assertThat(batch.isRunning()).isFalse();
    }

    @Test
    void repriseApresUnRedemarrageDuServeur() {
        when(trips.findFirstByTypeAndStatus(TripType.LIVETRACK, TripStatus.ACTIVE)).thenReturn(Optional.of(trip(9, TripStatus.ACTIVE)));

        batch.resumeAfterRestart();

        verify(scheduler).scheduleWithFixedDelay(any(Runnable.class), eq(INTERVAL));
        assertThat(batch.isRunning()).isTrue();
    }

    @Test
    void aucuneRepriseSiAucuneSessionNEstActive() {
        when(trips.findFirstByTypeAndStatus(TripType.LIVETRACK, TripStatus.ACTIVE)).thenReturn(Optional.empty());

        batch.resumeAfterRestart();

        verify(scheduler, never()).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));
        assertThat(batch.isRunning()).isFalse();
    }
}
