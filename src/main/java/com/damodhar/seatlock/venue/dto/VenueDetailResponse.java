package com.damodhar.seatlock.venue.dto;

import java.time.Instant;
import java.util.List;

public record VenueDetailResponse(Long id, String name, String city, Instant createdAt,
                                  long totalSeats, List<SectionSummary> sections) {

    public record SectionSummary(Long id, String name, long seatCount, List<RowSummary> rows) {
    }

    public record RowSummary(String label, long seats) {
    }
}
