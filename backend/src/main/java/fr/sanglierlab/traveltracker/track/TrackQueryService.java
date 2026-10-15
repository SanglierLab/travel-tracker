package fr.sanglierlab.traveltracker.track;

import fr.sanglierlab.traveltracker.config.AppProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

@Service
public class TrackQueryService {

    private final TrackPointRepository points;
    private final AppProperties properties;
    private final Clock clock;

    @Autowired
    public TrackQueryService(TrackPointRepository points, AppProperties properties) {
        this(points, properties, Clock.systemUTC());
    }

    TrackQueryService(TrackPointRepository points, AppProperties properties, Clock clock) {
        this.points = points;
        this.properties = properties;
        this.clock = clock;
    }

    /** Tracé de la partie publique : points du téléphone débarrassés des pics (voir {@link SpikeFilter}), découpés en lignes et allégés. */
    @Transactional(readOnly = true)
    public TrackResponse publicTrack() {
        Map<TrackSource, Long> lateAfter = new EnumMap<>(TrackSource.class);
        for (TrackSource source : TrackSource.values()) {
            lateAfter.put(source, properties.trackLateAfter().of(source).toSeconds());
        }
        return TrackSegmenter.build(SpikeFilter.apply(points.findAllRows(), clock.instant()), Duration.ofHours(properties.trackGapHours())).withLateAfter(lateAfter);
    }
}
