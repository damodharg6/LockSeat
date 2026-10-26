package com.damodhar.seatlock.venue.dto;

import java.time.Instant;

public record VenueResponse(Long id, String name, String city, Instant createdAt) {
}
