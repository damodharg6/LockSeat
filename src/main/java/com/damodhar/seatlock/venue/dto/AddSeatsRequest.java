package com.damodhar.seatlock.venue.dto;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A rectangular block of seats: every row in {@code rowLabels} gets seats 1..{@code seatsPerRow}.
 * The section is created if the venue does not have it yet.
 */
public record AddSeatsRequest(
        @NotBlank @Size(max = 60) String section,
        @NotEmpty @Size(max = 100) List<@NotNull @Pattern(regexp = "[A-Z]{1,3}",
                message = "must be 1-3 capital letters") String> rowLabels,
        @Min(1) @Max(200) int seatsPerRow) {
}
