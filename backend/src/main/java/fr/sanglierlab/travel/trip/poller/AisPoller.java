package fr.sanglierlab.travel.trip.poller;

import fr.sanglierlab.travel.trace.dto.IngestPointForm;
import fr.sanglierlab.travel.trip.Trip;
import fr.sanglierlab.travel.trip.TripType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Suivi de navire par AIS.
 *
 * Squelette, comme son homologue aérien. Particularité du domaine : la
 * couverture AIS terrestre s'arrête à quelques dizaines de milles des côtes.
 * Au large, seules les données satellitaires renseignent la position, et elles
 * sont rarement gratuites. Une traversée affichera donc probablement un tracé
 * discontinu — ce qui n'est pas un défaut à corriger, mais une réalité à
 * représenter honnêtement sur la carte.
 */
@Component
public class AisPoller implements TripPoller {

    private static final Logger log = LoggerFactory.getLogger(AisPoller.class);

    @Override
    public TripType supports() {
        return TripType.AIS;
    }

    @Override
    public List<IngestPointForm> poll(Trip trip) {
        log.debug("Suivi AIS non implémenté ; trajet {} ignoré", trip.getIdentifier());
        return List.of();
    }

    @Override
    public boolean looksFinished(Trip trip, List<IngestPointForm> lastPoll) {
        return false;
    }
}
