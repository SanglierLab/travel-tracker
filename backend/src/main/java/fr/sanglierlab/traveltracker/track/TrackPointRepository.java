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

    /** Nombre de points et heure du dernier point de chaque source (page Config). */
    @Query("""
            select new fr.sanglierlab.traveltracker.track.SourceStats(p.source, count(p), max(p.recordedAt))
            from TrackPoint p
            group by p.source
            """)
    List<SourceStats> sourceStats();

    /** Premier et dernier point de chaque trajet (sessions « Téléphone »). */
    @Query("""
            select new fr.sanglierlab.traveltracker.track.TripPointRange(p.tripId, count(p), min(p.recordedAt), max(p.recordedAt))
            from TrackPoint p
            where p.tripId is not null
            group by p.tripId
            """)
    List<TripPointRange> tripRanges();

    @Query("select max(p.recordedAt) from TrackPoint p where p.tripId = :tripId")
    LocalDateTime lastRecordedAt(@Param("tripId") Long tripId);

    /** Découpage d'une session : les points à partir de {@code at} passent dans l'autre trajet. À appeler dans une transaction. */
    @Modifying
    @Query("update TrackPoint p set p.tripId = :to where p.tripId = :from and p.recordedAt >= :at")
    int moveFrom(@Param("from") Long from, @Param("to") Long to, @Param("at") LocalDateTime at);

    /** À appeler dans une transaction. */
    @Modifying
    @Query("delete from TrackPoint p where p.tripId = :tripId")
    void deleteByTripId(@Param("tripId") Long tripId);

    /** Les points d'un trajet, du plus ancien au plus récent (export GPX). */
    List<TrackPoint> findByTripIdOrderByRecordedAtAscIdAsc(Long tripId);

    boolean existsBySourceAndRecordedAtAndLatitudeAndLongitude(
            TrackSource source, LocalDateTime recordedAt, BigDecimal latitude, BigDecimal longitude);
}
