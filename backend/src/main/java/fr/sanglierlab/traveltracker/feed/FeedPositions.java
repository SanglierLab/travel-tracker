package fr.sanglierlab.traveltracker.feed;

import java.time.LocalDate;
import java.util.Map;

/**
 * Où se trouve chaque étape dans le fil.
 *
 * @param today  date du jour côté serveur : une étape est « à venir » tant que son début est postérieur

 * @param upcomingDays une étape à venir qui commence dans ce nombre de jours (ou moins) est montrée en tête du fil, hors du bloc repliable
 * @param stages numéro de page (à partir de 1) des étapes déjà commencées, par identifiant ; les étapes à venir n'y figurent pas
 */
public record FeedPositions(LocalDate today, int upcomingDays, Map<Long, Integer> stages) {
}
