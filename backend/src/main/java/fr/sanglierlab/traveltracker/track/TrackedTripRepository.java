package fr.sanglierlab.traveltracker.track;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrackedTripRepository extends JpaRepository<TrackedTrip, Long> {

    Optional<TrackedTrip> findFirstByTypeAndStatus(TripType type, TripStatus status);

    List<TrackedTrip> findByTypeOrderByScheduledDepartureDescIdDesc(TripType type);
}
