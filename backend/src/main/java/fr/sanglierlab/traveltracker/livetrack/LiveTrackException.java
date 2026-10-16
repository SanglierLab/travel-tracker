package fr.sanglierlab.traveltracker.livetrack;

/**
 * Échec d'un appel à Garmin. Le message est toujours sans secret (ni jeton, ni adresse complète) et l'exception n'a pas de « cause » :
 * les erreurs HTTP de Spring citent l'adresse appelée, donc le jeton, et ne doivent pas atteindre les journaux.
 */
public class LiveTrackException extends RuntimeException {

    public LiveTrackException(String message) {
        super(message);
    }
}
