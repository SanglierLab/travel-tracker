package fr.sanglierlab.traveltracker.livetrack;

import java.util.Optional;

/** Transforme un lien court Garmin (https://gar.mn/…) en lien long LiveTrack, en suivant ses redirections. */
public interface LiveTrackLinkResolver {

    /**
     * @param shortLink lien court déjà validé par {@link LiveTrackLink#isShortLink}
     * @return le lien long, ou vide si le lien court ne mène pas à une session LiveTrack (inconnu, expiré, autre site)
     * @throws LiveTrackException si Garmin est injoignable
     */
    Optional<LiveTrackLink> resolve(String shortLink);
}
