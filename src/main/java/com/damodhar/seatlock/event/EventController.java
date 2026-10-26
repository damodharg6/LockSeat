package com.damodhar.seatlock.event;

import java.net.URI;
import java.time.Instant;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.damodhar.seatlock.auth.CurrentUser;
import com.damodhar.seatlock.common.web.PageResponse;
import com.damodhar.seatlock.event.dto.CreateEventRequest;
import com.damodhar.seatlock.event.dto.EventDetailResponse;
import com.damodhar.seatlock.event.dto.EventResponse;
import com.damodhar.seatlock.event.dto.EventSearch;
import com.damodhar.seatlock.event.dto.EventSeatResponse;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public ResponseEntity<EventDetailResponse> create(@Valid @RequestBody CreateEventRequest request,
                                                      @AuthenticationPrincipal Jwt jwt) {
        EventDetailResponse event = eventService.create(request, CurrentUser.from(jwt));
        return ResponseEntity.created(URI.create("/api/v1/events/" + event.id())).body(event);
    }

    @PostMapping("/{eventId}/publish")
    public EventDetailResponse publish(@PathVariable Long eventId, @AuthenticationPrincipal Jwt jwt) {
        return eventService.publish(eventId, CurrentUser.from(jwt));
    }

    @PostMapping("/{eventId}/cancel")
    public EventDetailResponse cancel(@PathVariable Long eventId, @AuthenticationPrincipal Jwt jwt) {
        return eventService.cancel(eventId, CurrentUser.from(jwt));
    }

    @GetMapping
    public PageResponse<EventResponse> list(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20) Pageable pageable) {
        return eventService.listPublished(new EventSearch(city, from, to), pageable);
    }

    @GetMapping("/{eventId}")
    public EventDetailResponse get(@PathVariable Long eventId, @AuthenticationPrincipal Jwt jwt) {
        return eventService.get(eventId, CurrentUser.from(jwt));
    }

    @GetMapping("/{eventId}/seats")
    public PageResponse<EventSeatResponse> seats(@PathVariable Long eventId,
                                                 @RequestParam(required = false) EventSeatStatus status,
                                                 @RequestParam(required = false) String section,
                                                 @PageableDefault(size = 50) Pageable pageable,
                                                 @AuthenticationPrincipal Jwt jwt) {
        return eventService.listSeats(eventId, status, section, pageable, CurrentUser.from(jwt));
    }
}
