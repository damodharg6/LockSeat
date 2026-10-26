package com.damodhar.seatlock.event.dto;

import java.time.Instant;

import com.damodhar.seatlock.event.EventStatus;

public record EventResponse(Long id, String title, Instant startsAt, EventStatus status,
                            VenueSummary venue, Long organiserId) {

    public record VenueSummary(Long id, String name, String city) {
    }
}
