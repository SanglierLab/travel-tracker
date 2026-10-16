package fr.sanglierlab.traveltracker.livetrack;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lien « à partager avec ses amis » d'une session Garmin LiveTrack : {@code https://livetrack.garmin.com/session/{uuid}/token/{jeton}}.
 * Le jeton donne accès à la position en direct : {@link #toString()} ne le montre jamais.
 */
public record LiveTrackLink(String sessionId, String token) {

    /** Le serveur n'appelle jamais l'adresse saisie : seuls l'identifiant et le jeton, validés ici, servent à construire les appels. */
    private static final Pattern LINK = Pattern.compile(
            "^https://livetrack\\.garmin\\.com/session/([0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12})/token/([0-9A-Za-z]{8,128})/?(?:[?#].*)?$");

    /** Lien court de partage Garmin (https://gar.mn/xxxx) : il redirige (HTTP 301) vers le lien long. */
    private static final Pattern SHORT_LINK = Pattern.compile("^https://gar\\.mn/[0-9A-Za-z]{3,40}/?$");

    /** @return vrai si le texte est un lien court Garmin, qu'il faut d'abord résoudre en lien long */
    public static boolean isShortLink(String raw) {
        return raw != null && SHORT_LINK.matcher(raw.strip()).matches();
    }

    /** @return le lien analysé, ou vide si le texte n'est pas un lien LiveTrack (mauvais site, jeton absent, http...) */
    public static Optional<LiveTrackLink> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        Matcher matcher = LINK.matcher(raw.strip());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(new LiveTrackLink(matcher.group(1).toLowerCase(Locale.ROOT), matcher.group(2)));
    }

    /** Début de l'identifiant de session, pour l'affichage et les journaux. */
    public static String shortId(String sessionId) {
        return sessionId.length() <= 8 ? sessionId : sessionId.substring(0, 8);
    }

    @Override
    public String toString() {
        return "LiveTrackLink[session=" + shortId(sessionId) + "…]";
    }
}
