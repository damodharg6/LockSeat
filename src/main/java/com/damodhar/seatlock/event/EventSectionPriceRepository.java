package com.damodhar.seatlock.event;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventSectionPriceRepository extends JpaRepository<EventSectionPrice, Long> {

    @EntityGraph(attributePaths = "section")
    List<EventSectionPrice> findByEventIdOrderBySectionName(Long eventId);
}
