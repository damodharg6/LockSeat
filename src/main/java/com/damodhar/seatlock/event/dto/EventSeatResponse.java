package com.damodhar.seatlock.event.dto;

import com.damodhar.seatlock.event.EventSeatStatus;

public record EventSeatResponse(Long id, Long seatId, String section, String row, int number,
                                long pricePaise, EventSeatStatus status) {
}
