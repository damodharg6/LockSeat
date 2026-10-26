package com.damodhar.seatlock.event.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.damodhar.seatlock.event.EventSeatStatus;
import com.damodhar.seatlock.event.EventStatus;
import com.damodhar.seatlock.event.dto.EventResponse.VenueSummary;

/** {@code seatCounts} is empty until the event is published and its seats exist. */
public record EventDetailResponse(Long id, String title, Instant startsAt, EventStatus status,
                                  VenueSummary venue, Long organiserId, Instant createdAt,
                                  Instant publishedAt, Instant cancelledAt,
                                  List<SectionPrice> sectionPrices, long totalSeats,
                                  Map<EventSeatStatus, Long> seatCounts) {

    public record SectionPrice(Long sectionId, String section, long pricePaise) {
    }
}
