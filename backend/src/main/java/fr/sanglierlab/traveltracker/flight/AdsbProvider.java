package fr.sanglierlab.traveltracker.flight;

import java.util.List;

/**
 * Source des positions ADS-B. C'est le point d'intégration du fournisseur de données : le batch ({@link AdsbBatch})
 * l'appelle à chaque cycle pour le vol actif.
 */
public interface AdsbProvider {

    /**
     * Positions récentes du vol.
     *
     * @param flightNumber numéro de vol (indicatif) du vol actif, tel que saisi dans l'administration, en majuscules,
     *                     sans espaces (par exemple « AFR1234 »)
     * @return les nouvelles positions à enregistrer, de la plus ancienne à la plus récente ; liste vide s'il n'y en a pas.
     *         Les positions déjà enregistrées sont ignorées sans erreur (même heure, mêmes coordonnées).
     */
    List<AdsbPosition> fetchPositions(String flightNumber);
}
