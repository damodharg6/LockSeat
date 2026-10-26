package com.damodhar.seatlock.venue;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SectionRepository extends JpaRepository<Section, Long> {

    Optional<Section> findByVenueIdAndName(Long venueId, String name);

    List<Section> findByVenueIdOrderByName(Long venueId);
}
