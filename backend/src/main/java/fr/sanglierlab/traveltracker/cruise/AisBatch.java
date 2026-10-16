package fr.sanglierlab.traveltracker.cruise;

import fr.sanglierlab.traveltracker.cruise.ais.VesselPositionProvider;
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
import java.util.Optional;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;

/**
 * Batch de récupération des positions AIS de la croisière active (parallèle de AdsbBatch : MMSI du navire au lieu de l'indicatif de vol).
 *
 * <ul>
 *   <li><b>Lancement / arrêt</b> : manuels, depuis l'administration (voir {@link CruiseService}). {@link #start} programme un
 *       cycle toutes les {@code app.ais-poll-interval} (5 minutes par défaut : un navire se déplace lentement), {@link #stop} l'annule.</li>
 *   <li><b>Une seule croisière à la fois.</b> La base fait foi : à chaque cycle, la croisière est relue et le batch s'arrête de lui-même si
 *       elle n'est plus active (arrêté, supprimé).</li>
 *   <li><b>Redémarrage du serveur</b> : le suivi d'une croisière active reprend tout seul.</li>
 *   <li><b>Un cycle qui échoue</b> (fournisseur indisponible, etc.) est journalisé et n'arrête pas le suivi : on réessaie au cycle suivant.</li>
 * </ul>
 */
@Component
public class AisBatch {

    private static final Logger log = LoggerFactory.getLogger(AisBatch.class);

    private final TrackedTripRepository trips;
    private final TrackPointService points;
    private final VesselPositionProvider provider;
    private final TaskScheduler scheduler;
    private final Duration interval;

    private ScheduledFuture<?> future;
    private Long runningTripId;
    /** Dernière position enregistrée du suivi en cours : une position identique n'est pas enregistrée une seconde fois. */
    private volatile AisPosition lastPosition;

    @Autowired
    public AisBatch(TrackedTripRepository trips, TrackPointService points, VesselPositionProvider provider,
                     TaskScheduler scheduler, @Value("${app.ais-poll-interval:5m}") Duration interval) {
        this.trips = trips;
        this.points = points;
        this.provider = provider;
        this.scheduler = scheduler;
        this.interval = interval;
    }

    /** Démarre le suivi de la croisière (le premier cycle part tout de suite). Remplace un éventuel suivi en cours. */
    /** Délai entre deux cycles de suivi (affiché dans la page Config). */
    public Duration interval() {
        return interval;
    }

    public synchronized void start(long tripId) {
        cancel();
        runningTripId = tripId;
        lastPosition = null;
        future = scheduler.scheduleWithFixedDelay(() -> tick(tripId), interval);
        log.info("AIS : suivi démarré pour le trajet {} (un cycle toutes les {})", tripId, interval);
    }

    /** Arrête le suivi s'il porte sur cette croisière ; sans effet sinon. */
    public synchronized void stop(long tripId) {
        if (Objects.equals(runningTripId, tripId)) {
            cancel();
            log.info("AIS : suivi arrêté pour le trajet {}", tripId);
        }
    }

    public synchronized boolean isRunning() {
        return future != null;
    }

    /** Après un redémarrage du serveur, reprend le suivi de la croisière resté actif. */
    @EventListener(ApplicationReadyEvent.class)
    public void resumeAfterRestart() {
        trips.findFirstByTypeAndStatus(TripType.BOAT, TripStatus.ACTIVE).ifPresent(trip -> start(trip.getId()));
    }

    /** Un cycle du batch. Visible pour les tests. */
    void tick(long tripId) {
        TrackedTrip trip = trips.findById(tripId).orElse(null);
        if (trip == null || trip.getStatus() != TripStatus.ACTIVE) {
            stop(tripId); // la croisière a été arrêté ou supprimé entre-temps
            return;
        }

        // MMSI du navire de la croisière active, par exemple « 227123456 » (voir VesselClient pour l'appel à l'API).
        String mmsi = trip.getIdentifier();

        try {
            Optional<AisPosition> position = provider.findPosition(mmsi);
            if (position.isEmpty()) {
                // Normal : navire hors de portée des stations AIS (large, zone non couverte), pas encore parti ou à quai sans émettre.
                log.info("AIS {} : aucune position disponible", mmsi);
                return;
            }
            AisPosition found = position.get();
            if (sameCoordinates(found, lastPosition)) {
                log.debug("AIS {} : position inchangée", mmsi);
                return;
            }
            TrackPointService.Result result = points.ingest(TrackSource.AIS, tripId,
                    BigDecimal.valueOf(found.latitude()), BigDecimal.valueOf(found.longitude()), null, found.recordedAt());
            lastPosition = found;
            log.info("AIS {} : position {}", mmsi, result == TrackPointService.Result.CREATED ? "enregistrée" : "déjà connue");
        } catch (RuntimeException e) {
            log.warn("AIS {} : échec de la récupération, nouvelle tentative au prochain cycle", mmsi, e);
        }
    }

    private static boolean sameCoordinates(AisPosition a, AisPosition b) {
        return b != null && a.latitude() == b.latitude() && a.longitude() == b.longitude();
    }

    private void cancel() {
        if (future != null) {
            future.cancel(false);
        }
        future = null;
        runningTripId = null;
        lastPosition = null;
    }
}
