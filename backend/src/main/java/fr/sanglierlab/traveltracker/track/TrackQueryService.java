package fr.sanglierlab.traveltracker.track;

import fr.sanglierlab.traveltracker.config.AppProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

@Service
public class TrackQueryService {

    private final TrackPointRepository points;
    private final AppProperties properties;

    public TrackQueryService(TrackPointRepository points, AppProperties properties) {
        this.points = points;
        this.properties = properties;
    }

    /** Tracé de la partie publique : tous les points, découpés en lignes et allégés. */
    @Transactional(readOnly = true)
    public TrackResponse publicTrack() {
        Map<TrackSource, Long> lateAfter = new EnumMap<>(TrackSource.class);
        for (TrackSource source : TrackSource.values()) {
            lateAfter.put(source, properties.trackLateAfter().of(source).toSeconds());
        }
        return TrackSegmenter.build(points.findAllRows(), Duration.ofHours(properties.trackGapHours())).withLateAfter(lateAfter);
    }
}
