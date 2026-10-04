package fr.sanglierlab.traveltracker.track;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface TrackPointRepository extends JpaRepository<TrackPoint, Long> {

    /** Tous les points, du plus ancien au plus récent, sans charger d'entités complètes. */
    @Query("""
            select new fr.sanglierlab.traveltracker.track.TrackRow(p.source, p.tripId, p.latitude, p.longitude, p.recordedAt)
            from TrackPoint p
            order by p.recordedAt asc, p.id asc
            """)
    List<TrackRow> findAllRows();

    boolean existsBySourceAndRecordedAtAndLatitudeAndLongitude(
            TrackSource source, LocalDateTime recordedAt, BigDecimal latitude, BigDecimal longitude);
}
