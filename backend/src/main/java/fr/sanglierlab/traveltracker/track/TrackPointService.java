package fr.sanglierlab.traveltracker.track;

import fr.sanglierlab.traveltracker.common.ApiException;
import fr.sanglierlab.traveltracker.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

@Service
public class TrackPointService {

    private static final Logger log = LoggerFactory.getLogger(TrackPointService.class);

    /** Tolérance sur l'horloge du téléphone : au-delà, la date est jugée fausse. */
    private static final Duration MAX_FUTURE = Duration.ofDays(1);

    /** Issue d'un ajout. Dans tous les cas, la réponse HTTP est 200 : GPSLogger ne doit pas réessayer un point écarté. */
    public enum Result {
        CREATED("created", null),
        DUPLICATE("duplicate", null),
        /** Pas de position réelle (0°/0°). */
        IGNORED_NO_FIX("ignored", "no-fix"),
        /** Précision annoncée trop mauvaise (voir app.track-max-accuracy-meters). */
        IGNORED_ACCURACY("ignored", "accuracy");

        private final String status;
        private final String reason;

        Result(String status, String reason) {
            this.status = status;
            this.reason = reason;
        }

        public String status() {
            return status;
        }

        public String reason() {
            return reason;
        }
    }

    private final TrackPointRepository points;
    private final int maxAccuracyMeters;
    private final Clock clock;

    @Autowired
    public TrackPointService(TrackPointRepository points, AppProperties properties) {
        this(points, properties.trackMaxAccuracyMeters(), Clock.systemUTC());
    }

    TrackPointService(TrackPointRepository points, int maxAccuracyMeters, Clock clock) {
        this.points = points;
        this.maxAccuracyMeters = maxAccuracyMeters;
        this.clock = clock;
    }

    /**
     * Enregistre un point, sauf s'il est écarté (voir {@link TrackPointFilter}). Idempotent : un point déjà reçu
     * (même source, même heure, mêmes coordonnées) est ignoré sans erreur, car GPSLogger renvoie parfois les mêmes points.
     *
     * @param accuracyMeters précision annoncée par le téléphone, ou null si inconnue
     */
    public Result ingest(TrackSource source, BigDecimal latitude, BigDecimal longitude, BigDecimal accuracyMeters,
                         Instant recordedAt) {
        return ingest(source, null, latitude, longitude, accuracyMeters, recordedAt);
    }

    /** Idem, pour un point rattaché à un trajet suivi (vol ADS-B) : {@code tripId} est alors celui du vol. */
    public Result ingest(TrackSource source, Long tripId, BigDecimal latitude, BigDecimal longitude,
                         BigDecimal accuracyMeters, Instant recordedAt) {
        if (recordedAt.isAfter(clock.instant().plus(MAX_FUTURE))) {
            throw ApiException.badRequest("La date du point est dans le futur : vérifiez l'heure du téléphone.");
        }
        // Mêmes arrondis que la base (DECIMAL(9,6), DECIMAL(7,1), DATETIME(3)) : la recherche de doublon doit comparer des valeurs identiques.
        BigDecimal lat = latitude.setScale(6, RoundingMode.HALF_UP);
        BigDecimal lon = longitude.setScale(6, RoundingMode.HALF_UP);
        BigDecimal accuracy = accuracyMeters == null ? null : accuracyMeters.setScale(1, RoundingMode.HALF_UP);
        LocalDateTime at = LocalDateTime.ofInstant(recordedAt, ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);

        TrackPointFilter.Verdict verdict = TrackPointFilter.check(lat, lon, accuracy, maxAccuracyMeters);
        if (verdict == TrackPointFilter.Verdict.NO_FIX) {
            log.info("Point ignoré : pas de position réelle (0°/0°), heure={}", at);
            return Result.IGNORED_NO_FIX;
        }
        if (verdict == TrackPointFilter.Verdict.POOR_ACCURACY) {
            log.info("Point ignoré : précision {} m (maximum {} m), heure={}", accuracy, maxAccuracyMeters, at);
            return Result.IGNORED_ACCURACY;
        }

        if (points.existsBySourceAndRecordedAtAndLatitudeAndLongitude(source, at, lat, lon)) {
            return Result.DUPLICATE;
        }
        try {
            points.saveAndFlush(new TrackPoint(source, tripId, lat, lon, accuracy, at));
        } catch (DataIntegrityViolationException e) {
            // Deux envois identiques arrivés en même temps : la contrainte d'unicité a tranché.
            return Result.DUPLICATE;
        }
        log.debug("Point enregistré : source={}, lat={}, lon={}, précision={}, heure={}", source, lat, lon, accuracy, at);
        return Result.CREATED;
    }
}
