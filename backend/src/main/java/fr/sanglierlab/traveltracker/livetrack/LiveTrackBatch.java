package fr.sanglierlab.traveltracker.livetrack;

import fr.sanglierlab.traveltracker.common.ApiException;
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
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;

/**
 * Batch de récupération des positions de la session LiveTrack active. Même principe que {@code AdsbBatch} :
 * <ul>
 *   <li><b>Lancement / arrêt</b> manuels, depuis l'administration (voir {@link LiveTrackService}) ; un cycle toutes les
 *       {@code app.livetrack-poll-interval} (1 minute par défaut : la montre envoie un point toutes les 10 s, mais un point par minute
 *       suffit à une carte, et chaque cycle est un appel à Garmin).</li>
 *   <li><b>Un seul suivi LiveTrack à la fois.</b> La base fait foi : à chaque cycle, la session est relue et le batch s'arrête de lui-même
 *       si elle n'est plus active.</li>
 *   <li><b>Incrémental</b> : seuls les points postérieurs au dernier point reçu sont demandés. Après un redémarrage du serveur, le suivi
 *       reprend avec l'historique récent ; les points déjà enregistrés sont ignorés (doublons).</li>
 *   <li><b>Fin de session</b> (événement END de Garmin, ou session disparue) : le suivi passe tout seul à « terminé ».</li>
 *   <li>Un cycle qui échoue est journalisé (sans jeton) et n'arrête pas le suivi : on réessaie au cycle suivant.</li>
 * </ul>
 */
@Component
public class LiveTrackBatch {

    private static final Logger log = LoggerFactory.getLogger(LiveTrackBatch.class);

    private final TrackedTripRepository trips;
    private final TrackPointService points;
    private final LiveTrackProvider provider;
    private final TaskScheduler scheduler;
    private final Duration interval;

    private ScheduledFuture<?> future;
    private Long runningTripId;
    /** Heure du dernier point reçu du suivi en cours (null : aucun, le premier appel demande l'historique récent). */
    private volatile Instant since;

    @Autowired
    public LiveTrackBatch(TrackedTripRepository trips, TrackPointService points, LiveTrackProvider provider,
                          TaskScheduler scheduler, @Value("${app.livetrack-poll-interval:60s}") Duration interval) {
        this.trips = trips;
        this.points = points;
        this.provider = provider;
        this.scheduler = scheduler;
        this.interval = interval;
    }

    /** Démarre le suivi de la session (le premier cycle part tout de suite). Remplace un éventuel suivi en cours. */
    /** Délai entre deux cycles de suivi (affiché dans la page Config). */
    public Duration interval() {
        return interval;
    }

    public synchronized void start(long tripId) {
        cancel();
        runningTripId = tripId;
        since = null;
        future = scheduler.scheduleWithFixedDelay(() -> tick(tripId), interval);
        log.info("LiveTrack : suivi démarré pour le trajet {} (un cycle toutes les {})", tripId, interval);
    }

    /** Arrête le suivi s'il porte sur cette session ; sans effet sinon. */
    public synchronized void stop(long tripId) {
        if (Objects.equals(runningTripId, tripId)) {
            cancel();
            log.info("LiveTrack : suivi arrêté pour le trajet {}", tripId);
        }
    }

    public synchronized boolean isRunning() {
        return future != null;
    }

    /** Après un redémarrage du serveur, reprend le suivi de la session restée active. */
    @EventListener(ApplicationReadyEvent.class)
    public void resumeAfterRestart() {
        trips.findFirstByTypeAndStatus(TripType.LIVETRACK, TripStatus.ACTIVE).ifPresent(trip -> start(trip.getId()));
    }

    /** Un cycle du batch. Visible pour les tests. */
    void tick(long tripId) {
        TrackedTrip trip = trips.findById(tripId).orElse(null);
        if (trip == null || trip.getStatus() != TripStatus.ACTIVE || trip.getAccessToken() == null) {
            stop(tripId); // la session a été arrêtée ou supprimée entre-temps
            return;
        }
        String reference = LiveTrackLink.shortId(trip.getIdentifier());

        try {
            LiveTrackUpdate update = provider.fetch(trip.getIdentifier(), trip.getAccessToken(), since);
            int created = 0;
            for (LiveTrackPoint point : update.points()) {
                try {
                    TrackPointService.Result result = points.ingest(TrackSource.GARMIN, tripId,
                            BigDecimal.valueOf(point.latitude()), BigDecimal.valueOf(point.longitude()), null, point.recordedAt());
                    if (result == TrackPointService.Result.CREATED) {
                        created++;
                    }
                } catch (ApiException e) {
                    // Un point refusé (par exemple daté du futur : horloge de la montre fausse) ne doit pas bloquer les suivants.
                    log.warn("LiveTrack {} : point ignoré ({})", reference, e.getMessage());
                }
                if (since == null || point.recordedAt().isAfter(since)) {
                    since = point.recordedAt();
                }
            }
            log.info("LiveTrack {} : {} point(s) reçu(s), {} enregistré(s)", reference, update.points().size(), created);
            if (update.ended()) {
                log.info("LiveTrack {} : Garmin signale la fin de la session", reference);
                finish(trip);
            }
        } catch (LiveTrackGoneException e) {
            log.info("LiveTrack {} : {}, suivi terminé", reference, e.getMessage());
            finish(trip);
        } catch (RuntimeException e) {
            // Message seul, sans pile d'appels : les erreurs HTTP de Spring citent l'adresse, donc le jeton (voir LiveTrackException).
            log.warn("LiveTrack {} : échec de la récupération ({}), nouvelle tentative au prochain cycle", reference, e.getMessage());
        }
    }

    private void finish(TrackedTrip trip) {
        trip.setStatus(TripStatus.FINISHED);
        trips.save(trip);
        stop(trip.getId());
    }

    private void cancel() {
        if (future != null) {
            future.cancel(false);
        }
        future = null;
        runningTripId = null;
        since = null;
    }
}
