package fr.sanglierlab.traveltracker.cruise.ais;

import fr.sanglierlab.traveltracker.cruise.AisPosition;

import java.util.Optional;

/**
 * Source des positions AIS : le batch ({@link fr.sanglierlab.traveltracker.cruise.AisBatch}) l'appelle à chaque cycle
 * pour la croisière active. Implémentation actuelle : {@link fr.sanglierlab.traveltracker.cruise.ais.api.VesselClient}
 * (appel au fournisseur de données pas encore branché).
 */
public interface VesselPositionProvider {

    /**
     * Dernière position connue du navire.
     *
     * @param mmsi MMSI du navire (9 chiffres) de la croisière active, tel que saisi dans l'administration
     * @return la position, ou {@link Optional#empty()} si le fournisseur n'en a pas (navire hors de portée des stations AIS,
     *         au large, à quai sans émettre...)
     * @throws RuntimeException si le fournisseur est injoignable ou répond en erreur : le batch journalise et réessaie au cycle suivant
     */
    Optional<AisPosition> findPosition(String mmsi);
}
