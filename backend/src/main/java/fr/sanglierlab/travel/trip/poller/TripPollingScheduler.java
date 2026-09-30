package fr.sanglierlab.travel.trip.poller;

import fr.sanglierlab.travel.trace.TraceService;
import fr.sanglierlab.travel.trace.dto.IngestPointForm;
import fr.sanglierlab.travel.trip.Trip;
import fr.sanglierlab.travel.trip.TripService;
import fr.sanglierlab.travel.trip.TripType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Ordonnanceur du suivi automatique.
 *
 * Trois tâches, volontairement séparées :
 *
 *   • activation des trajets dont l'heure de départ est atteinte, chaque minute ;
 *   • interrogation des sources pour les trajets en cours, selon la période
 *     configurée ;
 *   • clôture des trajets restés ouverts au-delà d'une durée maximale.
 *
 * Cette dernière n'est pas une précaution théorique : sans elle, un vol dont
 * l'arrivée n'aurait pas été détectée continuerait d'interroger indéfiniment
 * le fournisseur de données, jusqu'à épuisement du quota.
 *
 * Tant que les sources ne sont pas implémentées, l'interrogation ne produit
 * rien — le mécanisme est en place, prêt à recevoir la logique métier.
 */
@Component
public class TripPollingScheduler {

    private static final Logger log = LoggerFactory.getLogger(TripPollingScheduler.class);

    private final TripService trips;
    private final TraceService traces;
    private final Map<TripType, TripPoller> pollers = new EnumMap<>(TripType.class);
    private final long maximumDurationHours;

    public TripPollingScheduler(TripService trips,
                                TraceService traces,
                                List<TripPoller> availablePollers,
                                fr.sanglierlab.travel.config.AppProperties properties) {
        this.trips = trips;
        this.traces = traces;
        this.maximumDurationHours = properties.trip().maximumDurationHours();
        availablePollers.forEach(poller -> pollers.put(poller.supports(), poller));
    }

    /** Ouvre le suivi des trajets dont l'heure de départ est passée. */
    @Scheduled(fixedDelayString = "PT1M")
    public void activateDueTrips() {
        trips.activateDueTrips();
    }

    /** Interroge les sources pour chaque trajet en cours. */
    @Scheduled(fixedDelayString = "${app.trip.polling-interval:PT2M}")
    public void pollActiveTrips() {
        for (Trip trip : trips.activeTrips()) {
            TripPoller poller = pollers.get(trip.getType());
            if (poller == null) {
                continue;
            }
            try {
                List<IngestPointForm> positions = poller.poll(trip);
                if (!positions.isEmpty()) {
                    traces.ingest(positions);
                }
                trips.markPolled(trip.getId());

                if (poller.looksFinished(trip, positions)) {
                    trips.finish(trip.getId());
                }
            } catch (RuntimeException e) {
                // Une source indisponible ne doit pas interrompre les autres trajets
                log.warn("Interrogation du trajet {} en échec : {}",
                        trip.getIdentifier(), e.getMessage());
            }
        }
    }

    /** Clôt les trajets ouverts depuis trop longtemps. */
    @Scheduled(fixedDelayString = "PT30M")
    public void finishStaleTrips() {
        trips.finishStaleTrips(maximumDurationHours);
    }
}
