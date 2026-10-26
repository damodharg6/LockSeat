package com.damodhar.seatlock.venue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateVenueRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 80) String city) {
}
