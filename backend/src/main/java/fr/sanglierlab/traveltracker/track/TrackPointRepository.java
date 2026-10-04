package fr.sanglierlab.traveltracker.track;

import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface TrackPointRepository extends JpaRepository<TrackPoint, Long> {

    boolean existsBySourceAndRecordedAtAndLatitudeAndLongitude(
            TrackSource source, LocalDateTime recordedAt, BigDecimal latitude, BigDecimal longitude);
}
