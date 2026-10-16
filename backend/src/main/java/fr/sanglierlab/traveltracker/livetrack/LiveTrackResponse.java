package fr.sanglierlab.traveltracker.livetrack;

import fr.sanglierlab.traveltracker.track.TripStatus;

import java.time.Instant;

/**
 * Le jeton du lien n'est jamais renvoyé.
 *
 * @param label        nom affiché
 * @param reference    début de l'identifiant de session, pour reconnaître la session
 * @param registeredAt heure (UTC) de l'enregistrement
 * @param status       PLANNED (jamais démarrée), ACTIVE (suivi en cours) ou FINISHED (suivi arrêté ou session terminée)
 * @param pointCount   nombre de positions enregistrées
 * @param lastPointAt  heure (UTC) de la dernière position enregistrée, ou null
 */
public record LiveTrackResponse(long id, String label, String reference, Instant registeredAt, TripStatus status,
                                long pointCount, Instant lastPointAt) {
}
