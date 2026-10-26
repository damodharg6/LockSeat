package com.damodhar.seatlock.venue;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    boolean existsBySectionIdAndRowLabelIn(Long sectionId, Collection<String> rowLabels);

    @EntityGraph(attributePaths = "section")
    Page<Seat> findBySectionVenueId(Long venueId, Pageable pageable);

    /** Seat count per (section, row) for one venue, used to draw the seat map. */
    @Query("""
            select new com.damodhar.seatlock.venue.RowCount(s.section.id, s.rowLabel, count(s))
              from Seat s
             where s.section.venue.id = :venueId
             group by s.section.id, s.rowLabel
            """)
    List<RowCount> countRowsByVenue(Long venueId);
}
