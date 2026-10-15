package fr.sanglierlab.traveltracker.feed;

import java.time.LocalDate;
import java.util.Map;

/**
 * Où se trouve chaque étape dans le fil.
 *
 * @param today  date du jour côté serveur : une étape est « à venir » tant que son début est postérieur
 * @param stages numéro de page (à partir de 1) des étapes déjà commencées, par identifiant ; les étapes à venir n'y figurent pas
 */
public record FeedPositions(LocalDate today, Map<Long, Integer> stages) {
}
