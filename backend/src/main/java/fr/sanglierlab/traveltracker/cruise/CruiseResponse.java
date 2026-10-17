package fr.sanglierlab.traveltracker.cruise;

import fr.sanglierlab.traveltracker.track.TripStatus;

import java.time.Instant;

/**
 * @param identifier         MMSI du navire (9 chiffres)
 * @param scheduledDeparture date et heure (UTC) de départ ; minuit UTC quand aucune heure n'a été saisie
 * @param status             PLANNED (jamais démarré), ACTIVE (suivi en cours) ou FINISHED (suivi arrêté)
 * @param pointCount         nombre de positions enregistrées pour cette croisière
 * @param archived    true si le trajet est archivé (hors de la liste courante)
 * @param lastPointAt        heure (UTC) de la dernière position enregistrée, ou null
 */
public record CruiseResponse(long id, String identifier, Instant scheduledDeparture, TripStatus status,
                             boolean archived, long pointCount, Instant lastPointAt) {
}
