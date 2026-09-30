package fr.sanglierlab.travel.trip.poller;

import fr.sanglierlab.travel.trace.dto.IngestPointForm;
import fr.sanglierlab.travel.trip.Trip;

import java.util.List;

/**
 * Interrogation d'une source de positions externe.
 *
 * Une implémentation par type de trajet — ADS-B pour les vols, AIS pour les
 * navires. Le contrat se limite au strict nécessaire : les fournisseurs de
 * données restent à choisir, et rien ici ne présume de leur interface.
 */
public interface TripPoller {

    /** Type de trajet pris en charge. */
    fr.sanglierlab.travel.trip.TripType supports();

    /**
     * Relève les positions connues depuis la dernière interrogation.
     *
     * @return positions recueillies, éventuellement vide
     */
    List<IngestPointForm> poll(Trip trip);

    /**
     * Indique si le trajet paraît achevé — appareil posé, navire à quai, ou
     * absence prolongée de signal. L'ordonnanceur clôt alors le suivi.
     */
    boolean looksFinished(Trip trip, List<IngestPointForm> lastPoll);
}
