package fr.sanglierlab.traveltracker.livetrack;

import java.time.Instant;

/**
 * Source des positions d'une session LiveTrack : le batch ({@link LiveTrackBatch}) l'appelle à chaque cycle.
 * Implémentation actuelle : {@link fr.sanglierlab.traveltracker.livetrack.api.GarminLiveTrackClient}.
 */
public interface LiveTrackProvider {

    /**
     * Points de la session reçus depuis {@code since}.
     *
     * @param sessionId identifiant de session (UUID du lien de partage)
     * @param token     jeton du lien de partage
     * @param since     heure du dernier point déjà connu, ou null au premier appel (le fournisseur renvoie alors l'historique récent) ;
     *                  le point de cette heure peut être renvoyé une seconde fois : l'enregistrement ignore les doublons
     * @throws LiveTrackGoneException si la session n'existe plus
     * @throws LiveTrackException     si Garmin est injoignable ou répond en erreur : le batch journalise et réessaie au cycle suivant
     */
    LiveTrackUpdate fetch(String sessionId, String token, Instant since);
}
