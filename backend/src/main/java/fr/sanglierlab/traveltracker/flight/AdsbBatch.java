package fr.sanglierlab.traveltracker.flight;

import fr.sanglierlab.traveltracker.track.TrackPointService;
import fr.sanglierlab.traveltracker.track.TrackSource;
import fr.sanglierlab.traveltracker.track.TrackedTrip;
import fr.sanglierlab.traveltracker.track.TrackedTripRepository;
import fr.sanglierlab.traveltracker.track.TripStatus;
import fr.sanglierlab.traveltracker.track.TripType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;

/**
 * Batch de récupération des positions ADS-B du vol actif.
 *
 * <ul>
 *   <li><b>Lancement / arrêt</b> : manuels, depuis l'administration (voir {@link FlightService}). {@link #start} programme un
 *       cycle toutes les {@code app.adsb-poll-interval} (30 s par défaut), {@link #stop} l'annule.</li>
 *   <li><b>Un seul vol à la fois.</b> La base fait foi : à chaque cycle, le vol est relu et le batch s'arrête de lui-même s'il
 *       n'est plus actif (arrêté, supprimé).</li>
 *   <li><b>Redémarrage du serveur</b> : le suivi d'un vol actif reprend tout seul.</li>
 *   <li><b>Un cycle qui échoue</b> (fournisseur indisponible, etc.) est journalisé et n'arrête pas le suivi : on réessaie au cycle suivant.</li>
 * </ul>
 */
@Component
public class AdsbBatch {

    private static final Logger log = LoggerFactory.getLogger(AdsbBatch.class);

    private final TrackedTripRepository trips;
    private final TrackPointService points;
    private final AdsbProvider provider;
    private final TaskScheduler scheduler;
    private final Duration interval;

    private ScheduledFuture<?> future;
    private Long runningTripId;

    @Autowired
    public AdsbBatch(TrackedTripRepository trips, TrackPointService points, AdsbProvider provider,
                     TaskScheduler scheduler, @Value("${app.adsb-poll-interval:30s}") Duration interval) {
        this.trips = trips;
        this.points = points;
        this.provider = provider;
        this.scheduler = scheduler;
        this.interval = interval;
    }

    /** Démarre le suivi du vol (le premier cycle part tout de suite). Remplace un éventuel suivi en cours. */
    public synchronized void start(long tripId) {
        cancel();
        runningTripId = tripId;
        future = scheduler.scheduleWithFixedDelay(() -> tick(tripId), interval);
        log.info("ADS-B : suivi démarré pour le trajet {} (un cycle toutes les {})", tripId, interval);
    }

    /** Arrête le suivi s'il porte sur ce vol ; sans effet sinon. */
    public synchronized void stop(long tripId) {
        if (Objects.equals(runningTripId, tripId)) {
            cancel();
            log.info("ADS-B : suivi arrêté pour le trajet {}", tripId);
        }
    }

    public synchronized boolean isRunning() {
        return future != null;
    }

    /** Après un redémarrage du serveur, reprend le suivi du vol resté actif. */
    @EventListener(ApplicationReadyEvent.class)
    public void resumeAfterRestart() {
        trips.findFirstByTypeAndStatus(TripType.PLANE, TripStatus.ACTIVE).ifPresent(trip -> start(trip.getId()));
    }

    /** Un cycle du batch. Visible pour les tests. */
    void tick(long tripId) {
        TrackedTrip trip = trips.findById(tripId).orElse(null);
        if (trip == null || trip.getStatus() != TripStatus.ACTIVE) {
            stop(tripId); // le vol a été arrêté ou supprimé entre-temps
            return;
        }

        // ┌───────────────────────────────────────────────────────────────────────────────────────┐
        // │ Numéro de vol (indicatif) du vol actif, par exemple « AFR1234 ».                       │
        // │ L'appel à l'API du fournisseur ADS-B se branche dans AdsbProvider.fetchPositions(...), │
        // │ qui reçoit cette valeur (voir PlaceholderAdsbProvider : TODO).                         │
        // └───────────────────────────────────────────────────────────────────────────────────────┘
        String flightNumber = trip.getIdentifier();

        try {
            List<AdsbPosition> positions = provider.fetchPositions(flightNumber);
            int saved = 0;
            for (AdsbPosition position : positions) {
                TrackPointService.Result result = points.ingest(TrackSource.ADSB, tripId,
                        BigDecimal.valueOf(position.latitude()), BigDecimal.valueOf(position.longitude()),
                        null, position.recordedAt());
                if (result == TrackPointService.Result.CREATED) {
                    saved++;
                }
            }
            log.info("ADS-B {} : {} position(s) reçue(s), {} enregistrée(s)", flightNumber, positions.size(), saved);
        } catch (RuntimeException e) {
            log.warn("ADS-B {} : échec de la récupération, nouvelle tentative au prochain cycle", flightNumber, e);
        }
    }

    private void cancel() {
        if (future != null) {
            future.cancel(false);
        }
        future = null;
        runningTripId = null;
    }
}
