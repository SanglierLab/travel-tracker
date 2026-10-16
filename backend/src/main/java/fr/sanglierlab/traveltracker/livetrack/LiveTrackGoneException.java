package fr.sanglierlab.traveltracker.livetrack;

/** La session n'existe plus chez Garmin (lien expiré ou supprimé) : inutile de réessayer, le suivi doit s'arrêter. */
public class LiveTrackGoneException extends LiveTrackException {

    public LiveTrackGoneException(String message) {
        super(message);
    }
}
