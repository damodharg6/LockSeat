package com.damodhar.seatlock.event;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface EventSeatRepository extends JpaRepository<EventSeat, Long>, JpaSpecificationExecutor<EventSeat> {

    /**
     * Creates every event seat for a published event in one set-based statement: one row per
     * venue seat, priced from its section. Postgres does the work in a single round trip, however
     * many seats the venue has.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO event_seat (id, event_id, seat_id, section_id, price_paise, status, version)
            SELECT nextval('event_seat_seq'), p.event_id, s.id, s.section_id, p.price_paise, 'AVAILABLE', 0
              FROM event_section_price p
              JOIN seat s ON s.section_id = p.section_id
             WHERE p.event_id = :eventId
            """)
    int generateForEvent(Long eventId);

    long countByEventId(Long eventId);

    @Query("select es.status as status, count(es) as seats from EventSeat es where es.event.id = :eventId group by es.status")
    List<StatusCount> countByStatus(Long eventId);

    @Override
    @EntityGraph(attributePaths = {"seat", "section"})
    Page<EventSeat> findAll(Specification<EventSeat> spec, Pageable pageable);

    interface StatusCount {
        EventSeatStatus getStatus();

        long getSeats();
    }
}
