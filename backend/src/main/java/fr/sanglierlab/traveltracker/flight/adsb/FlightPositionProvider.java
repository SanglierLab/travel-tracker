package fr.sanglierlab.traveltracker.flight.adsb;

import fr.sanglierlab.traveltracker.flight.AdsbPosition;

import java.util.Optional;

/**
 * Source des positions ADS-B : le batch ({@link fr.sanglierlab.traveltracker.flight.AdsbBatch}) l'appelle à chaque cycle
 * pour le vol actif. Implémentation actuelle : {@link fr.sanglierlab.traveltracker.flight.adsb.api.AdsbLolClient}.
 */
public interface FlightPositionProvider {

    /**
     * Dernière position connue du vol.
     *
     * @param callsign indicatif du vol actif, tel que saisi dans l'administration (par exemple « AFR186 »)
     * @return la position, ou {@link Optional#empty()} si le fournisseur n'en a pas (avion hors de portée des récepteurs,
     *         par exemple au-dessus de l'Arctique ou de l'océan, vol pas encore parti ou déjà posé)
     * @throws RuntimeException si le fournisseur est injoignable ou répond en erreur : le batch journalise et réessaie au cycle suivant
     */
    Optional<AdsbPosition> findPosition(String callsign);
}
