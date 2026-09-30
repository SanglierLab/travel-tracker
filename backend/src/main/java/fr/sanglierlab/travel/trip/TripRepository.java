package fr.sanglierlab.travel.trip;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface TripRepository extends JpaRepository<Trip, Long> {

    List<Trip> findAllByOrderByScheduledStartDesc();

    List<Trip> findByStatus(TripStatus status);

    /**
     * Trajets planifiés dont l'heure de départ est atteinte : ceux que
     * l'ordonnanceur doit activer.
     */
    @Query("""
            SELECT t FROM Trip t
            WHERE t.status = fr.sanglierlab.travel.trip.TripStatus.PLANNED
              AND t.scheduledStart <= :now
            ORDER BY t.scheduledStart ASC
            """)
    List<Trip> findDueForStart(@Param("now") Instant now);

    /**
     * Trajets en cours depuis trop longtemps.
     * Une interrogation qui ne se termine jamais consommerait inutilement des
     * requêtes vers les fournisseurs de données : on impose une échéance.
     */
    @Query("""
            SELECT t FROM Trip t
            WHERE t.status = fr.sanglierlab.travel.trip.TripStatus.ACTIVE
              AND t.startedAt < :before
            """)
    List<Trip> findStaleActive(@Param("before") Instant before);
}
