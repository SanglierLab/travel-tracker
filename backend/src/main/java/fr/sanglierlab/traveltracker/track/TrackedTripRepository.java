package fr.sanglierlab.traveltracker.track;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrackedTripRepository extends JpaRepository<TrackedTrip, Long> {

    Optional<TrackedTrip> findFirstByTypeAndStatus(TripType type, TripStatus status);

    List<TrackedTrip> findByTypeOrderByScheduledDepartureDescIdDesc(TripType type);

    /** La session « Téléphone » la plus récente commencée à cette heure ou avant. */
    Optional<TrackedTrip> findFirstByTypeAndScheduledDepartureLessThanEqualOrderByScheduledDepartureDescIdDesc(
            TripType type, java.time.LocalDateTime at);

    boolean existsByTypeAndIdentifier(TripType type, String identifier);
}
