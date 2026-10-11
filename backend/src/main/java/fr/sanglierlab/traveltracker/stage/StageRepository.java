package fr.sanglierlab.traveltracker.stage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface StageRepository extends JpaRepository<Stage, Long> {

    List<Stage> findAllByOrderBySortOrderAscIdAsc();

    @Query("select coalesce(max(s.sortOrder), 0) from Stage s")
    int maxSortOrder();
}
