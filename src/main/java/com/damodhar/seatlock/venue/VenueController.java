package com.damodhar.seatlock.venue;

import java.net.URI;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.damodhar.seatlock.common.web.PageResponse;
import com.damodhar.seatlock.venue.dto.AddSeatsRequest;
import com.damodhar.seatlock.venue.dto.AddSeatsResponse;
import com.damodhar.seatlock.venue.dto.CreateVenueRequest;
import com.damodhar.seatlock.venue.dto.SeatResponse;
import com.damodhar.seatlock.venue.dto.VenueDetailResponse;
import com.damodhar.seatlock.venue.dto.VenueResponse;

@RestController
@RequestMapping("/api/v1/venues")
public class VenueController {

    private final VenueService venueService;

    public VenueController(VenueService venueService) {
        this.venueService = venueService;
    }

    @PostMapping
    public ResponseEntity<VenueResponse> create(@Valid @RequestBody CreateVenueRequest request) {
        VenueResponse venue = venueService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/venues/" + venue.id())).body(venue);
    }

    @GetMapping
    public PageResponse<VenueResponse> list(@PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return venueService.list(pageable);
    }

    @GetMapping("/{venueId}")
    public VenueDetailResponse get(@PathVariable Long venueId) {
        return venueService.get(venueId);
    }

    @PostMapping("/{venueId}/seats")
    @ResponseStatus(HttpStatus.CREATED)
    public AddSeatsResponse addSeats(@PathVariable Long venueId, @Valid @RequestBody AddSeatsRequest request) {
        return venueService.addSeats(venueId, request);
    }

    @GetMapping("/{venueId}/seats")
    public PageResponse<SeatResponse> listSeats(
            @PathVariable Long venueId,
            @PageableDefault(size = 50, sort = {"section.name", "rowLabel", "seatNumber"}) Pageable pageable) {
        return venueService.listSeats(venueId, pageable);
    }
}
