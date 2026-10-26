package com.damodhar.seatlock.event.dto;

import java.time.Instant;

/** Optional filters for the public event list; null means "no filter". */
public record EventSearch(String city, Instant from, Instant to) {
}
