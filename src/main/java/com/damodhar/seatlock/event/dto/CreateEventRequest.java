package com.damodhar.seatlock.event.dto;

import java.time.Instant;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** {@code sectionPrices} maps section name to price in paise, e.g. {"Stalls": 49900}. */
public record CreateEventRequest(
        @NotNull Long venueId,
        @NotBlank @Size(max = 200) String title,
        @NotNull Instant startsAt,
        @NotEmpty Map<@NotBlank String, @NotNull @PositiveOrZero Long> sectionPrices) {
}
