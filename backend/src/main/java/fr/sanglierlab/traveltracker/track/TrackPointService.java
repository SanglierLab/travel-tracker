package fr.sanglierlab.traveltracker.track;

import fr.sanglierlab.traveltracker.common.ApiException;
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

    public enum Result {
        CREATED, DUPLICATE
    }

    private final TrackPointRepository points;
    private final Clock clock;

    @Autowired
    public TrackPointService(TrackPointRepository points) {
        this(points, Clock.systemUTC());
    }

    TrackPointService(TrackPointRepository points, Clock clock) {
        this.points = points;
        this.clock = clock;
    }

    /**
     * Enregistre un point. Idempotent : un point déjà reçu (même source, même heure, mêmes coordonnées) est ignoré
     * sans erreur, car GPSLogger renvoie parfois les mêmes points.
     */
    public Result ingest(TrackSource source, BigDecimal latitude, BigDecimal longitude, Instant recordedAt) {
        if (recordedAt.isAfter(clock.instant().plus(MAX_FUTURE))) {
            throw ApiException.badRequest("La date du point est dans le futur : vérifiez l'heure du téléphone.");
        }
        // Mêmes arrondis que la base (DECIMAL(9,6), DATETIME(3)) : la recherche de doublon doit comparer des valeurs identiques.
        BigDecimal lat = latitude.setScale(6, RoundingMode.HALF_UP);
        BigDecimal lon = longitude.setScale(6, RoundingMode.HALF_UP);
        LocalDateTime at = LocalDateTime.ofInstant(recordedAt, ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);

        if (points.existsBySourceAndRecordedAtAndLatitudeAndLongitude(source, at, lat, lon)) {
            return Result.DUPLICATE;
        }
        try {
            points.saveAndFlush(new TrackPoint(source, lat, lon, at));
        } catch (DataIntegrityViolationException e) {
            // Deux envois identiques arrivés en même temps : la contrainte d'unicité a tranché.
            return Result.DUPLICATE;
        }
        log.debug("Point enregistré : source={}, lat={}, lon={}, heure={}", source, lat, lon, at);
        return Result.CREATED;
    }
}
