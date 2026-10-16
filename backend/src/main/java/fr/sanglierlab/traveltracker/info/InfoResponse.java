package fr.sanglierlab.traveltracker.info;

import fr.sanglierlab.traveltracker.track.TrackSource;

import java.time.Instant;
import java.util.List;

/**
 * Page Config de l'administration (lecture seule). Ne contient jamais de secret : ni mot de passe, ni token d'API, ni jeton LiveTrack.
 */
public record InfoResponse(Version version, Limits limits, List<Source> sources, Content content, Tools tools) {

    /** @param builtAt date de construction, ou null en développement (sans build-info) */
    public record Version(String name, String version, Instant builtAt) {
    }

    public record Limits(long maxPhotoBytes, long maxVideoBytes, int pageSize, int upcomingDays, int trackGapHours,
                         int trackMaxAccuracyMeters) {
    }

    /**
     * Une source de position.
     *
     * @param pollSeconds    délai entre deux relevés du fournisseur ; null pour le téléphone (il envoie lui-même)
     * @param lateAfterSeconds délai sans position avant le style « en retard »
     * @param trackable      vrai si la source a un suivi à activer (vol, croisière, montre) ; faux pour le téléphone
     * @param activeLabel    nom du suivi en cours, ou null s'il n'y en a pas
     */
    public record Source(TrackSource source, Long pollSeconds, long lateAfterSeconds, boolean trackable, String activeLabel,
                         long pointCount, Instant lastPointAt) {
    }

    /** @param diskFreeBytes espace libre là où sont les médias, ou null s'il ne peut pas être lu */
    public record Content(long galleries, long photos, long videos, long texts, long originalsBytes, Long diskFreeBytes,
                          Long diskTotalBytes) {
    }

    /** @param positionsPath chemin de l'API de positions (le token n'est jamais donné) */
    public record Tools(boolean ffmpegAvailable, String ffmpegVersion, String positionsPath, String tokenHeader) {
    }
}
