package com.damodhar.seatlock.venue;

/** Projection: how many seats a row of a section has. */
public record RowCount(Long sectionId, String rowLabel, long seats) {
}
