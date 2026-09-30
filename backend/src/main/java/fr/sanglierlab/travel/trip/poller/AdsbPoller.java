package fr.sanglierlab.travel.trip.poller;

import fr.sanglierlab.travel.trace.dto.IngestPointForm;
import fr.sanglierlab.travel.trip.Trip;
import fr.sanglierlab.travel.trip.TripType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Suivi de vol par ADS-B.
 *
 * Squelette délibéré : la source de données n'est pas encore arrêtée. Une fois
 * le fournisseur choisi, seule la méthode {@link #poll(Trip)} est à écrire —
 * l'ordonnanceur, le stockage et l'affichage sont déjà en place et n'auront
 * pas à changer.
 *
 * Pistes possibles : OpenSky Network, qui propose un accès libre limité, ou
 * adsb.lol. Points de vigilance : quota d'appels, couverture au-dessus des
 * océans souvent lacunaire, et correspondance entre indicatif commercial et
 * indicatif radio, qui ne coïncident pas toujours.
 */
@Component
public class AdsbPoller implements TripPoller {

    private static final Logger log = LoggerFactory.getLogger(AdsbPoller.class);

    @Override
    public TripType supports() {
        return TripType.ADSB;
    }

    @Override
    public List<IngestPointForm> poll(Trip trip) {
        log.debug("Suivi ADS-B non implémenté ; trajet {} ignoré", trip.getIdentifier());
        return List.of();
    }

    @Override
    public boolean looksFinished(Trip trip, List<IngestPointForm> lastPoll) {
        return false;
    }
}
