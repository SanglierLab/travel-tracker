package fr.sanglierlab.traveltracker.track;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** Nombre de points et heure du dernier point de chaque trajet suivi. */
    @Query("""
            select new fr.sanglierlab.traveltracker.track.TripPointStats(p.tripId, count(p), max(p.recordedAt))
            from TrackPoint p
            where p.tripId is not null
            group by p.tripId
            """)
    List<TripPointStats> tripStats();

    /** À appeler dans une transaction. */
    @Modifying
    @Query("delete from TrackPoint p where p.tripId = :tripId")
    void deleteByTripId(@Param("tripId") Long tripId);

    boolean existsBySourceAndRecordedAtAndLatitudeAndLongitude(
            TrackSource source, LocalDateTime recordedAt, BigDecimal latitude, BigDecimal longitude);
}
