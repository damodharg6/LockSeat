package com.damodhar.seatlock.venue.dto;

public record SeatResponse(Long id, Long sectionId, String section, String row, int number) {
}
