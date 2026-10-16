package fr.sanglierlab.traveltracker.livetrack;

import java.util.List;

/**
 * Réponse d'un cycle de suivi.
 *
 * @param points nouveaux points, du plus ancien au plus récent (peut être vide : rien de nouveau)
 * @param ended  vrai si Garmin a signalé la fin de la session (événement END) : le suivi peut s'arrêter
 */
public record LiveTrackUpdate(List<LiveTrackPoint> points, boolean ended) {
}
