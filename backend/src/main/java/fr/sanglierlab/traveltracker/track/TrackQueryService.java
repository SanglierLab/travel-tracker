package fr.sanglierlab.traveltracker.track;

import fr.sanglierlab.traveltracker.config.AppProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

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
        return TrackSegmenter.build(points.findAllRows(), Duration.ofHours(properties.trackGapHours()));
    }
}
