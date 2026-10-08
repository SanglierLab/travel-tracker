package fr.sanglierlab.traveltracker.flight;

import fr.sanglierlab.traveltracker.track.TripStatus;

import java.time.Instant;

/**
 * @param identifier         numéro de vol (indicatif)
 * @param scheduledDeparture date et heure (UTC) de départ ; minuit UTC quand aucune heure n'a été saisie
 * @param status             PLANNED (jamais démarré), ACTIVE (suivi en cours) ou FINISHED (suivi arrêté)
 * @param pointCount         nombre de positions enregistrées pour ce vol
 * @param lastPointAt        heure (UTC) de la dernière position enregistrée, ou null
 */
public record FlightResponse(long id, String identifier, Instant scheduledDeparture, TripStatus status,
                             long pointCount, Instant lastPointAt) {
}
