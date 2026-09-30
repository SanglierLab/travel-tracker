package fr.sanglierlab.travel.trace;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface TracePointRepository extends JpaRepository<TracePoint, Long> {

    /**
     * Points d'une source sur une période, dans l'ordre du parcours.
     * Les bornes sont facultatives : absentes, toute la trace est renvoyée.
     */
    @Query("""
            SELECT p FROM TracePoint p
            WHERE (:source IS NULL OR p.source = :source)
              AND (:from   IS NULL OR p.measuredAt >= :from)
              AND (:to     IS NULL OR p.measuredAt <= :to)
            ORDER BY p.measuredAt ASC
            """)
    List<TracePoint> findForMap(@Param("source") TraceSource source,
                                @Param("from") Instant from,
                                @Param("to") Instant to);

    List<TracePoint> findByTripIdOrderByMeasuredAtAsc(Long tripId);

    /** Liste d'administration, la plus récente d'abord. */
    Page<TracePoint> findAllByOrderByMeasuredAtDesc(Pageable pageable);

    Page<TracePoint> findBySourceOrderByMeasuredAtDesc(TraceSource source, Pageable pageable);

    /** Dernière position connue, pour centrer la carte à l'ouverture. */
    @Query("""
            SELECT p FROM TracePoint p
            WHERE p.source = :source
            ORDER BY p.measuredAt DESC
            LIMIT 1
            """)
    TracePoint findLatest(@Param("source") TraceSource source);

    boolean existsBySourceAndMeasuredAtAndTripId(TraceSource source, Instant measuredAt, Long tripId);

    boolean existsBySourceAndMeasuredAtAndTripIsNull(TraceSource source, Instant measuredAt);

    long countBySource(TraceSource source);
}
