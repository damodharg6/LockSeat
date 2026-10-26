package com.damodhar.seatlock.event;

import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.damodhar.seatlock.auth.CurrentUser;
import com.damodhar.seatlock.common.error.BadRequestException;
import com.damodhar.seatlock.common.error.ForbiddenException;
import com.damodhar.seatlock.common.error.NotFoundException;
import com.damodhar.seatlock.common.web.PageResponse;
import com.damodhar.seatlock.event.dto.CreateEventRequest;
import com.damodhar.seatlock.event.dto.EventDetailResponse;
import com.damodhar.seatlock.event.dto.EventDetailResponse.SectionPrice;
import com.damodhar.seatlock.event.dto.EventResponse;
import com.damodhar.seatlock.event.dto.EventResponse.VenueSummary;
import com.damodhar.seatlock.event.dto.EventSearch;
import com.damodhar.seatlock.event.dto.EventSeatResponse;
import com.damodhar.seatlock.user.UserRepository;
import com.damodhar.seatlock.venue.Section;
import com.damodhar.seatlock.venue.SectionRepository;
import com.damodhar.seatlock.venue.Venue;
import com.damodhar.seatlock.venue.VenueRepository;

@Service
public class EventService {

    private final EventRepository events;
    private final EventSectionPriceRepository prices;
    private final EventSeatRepository eventSeats;
    private final VenueRepository venues;
    private final SectionRepository sections;
    private final UserRepository users;
    private final Clock clock;

    public EventService(EventRepository events, EventSectionPriceRepository prices, EventSeatRepository eventSeats,
                        VenueRepository venues, SectionRepository sections, UserRepository users, Clock clock) {
        this.events = events;
        this.prices = prices;
        this.eventSeats = eventSeats;
        this.venues = venues;
        this.sections = sections;
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public EventDetailResponse create(CreateEventRequest request, CurrentUser organiser) {
        if (!request.startsAt().isAfter(clock.instant())) {
            throw new BadRequestException("startsAt must be in the future.");
        }
        Venue venue = venues.findById(request.venueId())
                .orElseThrow(() -> new NotFoundException("Venue " + request.venueId() + " not found."));
        Map<String, Section> venueSections = sectionsByName(venue.getId());
        checkPricesCoverSections(venueSections.keySet(), request.sectionPrices().keySet());

        Event event = events.save(new Event(venue, users.getReferenceById(organiser.id()),
                request.title().strip(), request.startsAt()));
        prices.saveAll(request.sectionPrices().entrySet().stream()
                .map(e -> new EventSectionPrice(event, venueSections.get(e.getKey()), e.getValue()))
                .toList());
        return detail(event);
    }

    /**
     * DRAFT -> PUBLISHED and generate the event's seats. The status change is flushed first, so if
     * two publish requests race, the second fails its @Version check (409) before inserting
     * anything; the unique (event_id, seat_id) constraint is the backstop.
     */
    @Transactional
    public EventDetailResponse publish(Long eventId, CurrentUser caller) {
        Event event = findManageable(eventId, caller);
        event.checkCanMoveTo(EventStatus.PUBLISHED);
        if (!event.getStartsAt().isAfter(clock.instant())) {
            throw new BadRequestException("Event " + eventId + " has already started and cannot be published.");
        }
        Set<String> priced = prices.findByEventIdOrderBySectionName(eventId).stream()
                .map(p -> p.getSection().getName()).collect(Collectors.toSet());
        checkPricesCoverSections(sectionsByName(event.getVenue().getId()).keySet(), priced);

        event.publish(clock.instant());
        events.saveAndFlush(event);
        int created = eventSeats.generateForEvent(eventId);
        if (created == 0) {
            throw new BadRequestException("Venue " + event.getVenue().getId() + " has no seats to sell.");
        }
        return detail(event);
    }

    @Transactional
    public EventDetailResponse cancel(Long eventId, CurrentUser caller) {
        Event event = findManageable(eventId, caller);
        event.cancel(clock.instant());
        events.saveAndFlush(event);
        return detail(event);
    }

    /** Public list: published events only, soonest first. Client sort parameters are ignored. */
    @Transactional(readOnly = true)
    public PageResponse<EventResponse> listPublished(EventSearch search, Pageable pageable) {
        if (search.from() != null && search.to() != null && search.from().isAfter(search.to())) {
            throw new BadRequestException("'from' must not be after 'to'.");
        }
        Specification<Event> spec = EventSpecifications.published()
                .and(EventSpecifications.inCity(search.city()))
                .and(EventSpecifications.startsFrom(search.from()))
                .and(EventSpecifications.startsBefore(search.to()));
        Pageable byStart = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by("startsAt", "id"));
        return PageResponse.of(events.findAll(spec, byStart), EventService::summary);
    }

    @Transactional(readOnly = true)
    public EventDetailResponse get(Long eventId, CurrentUser caller) {
        return detail(findVisible(eventId, caller));
    }

    @Transactional(readOnly = true)
    public PageResponse<EventSeatResponse> listSeats(Long eventId, EventSeatStatus status, String section,
                                                     Pageable pageable, CurrentUser caller) {
        findVisible(eventId, caller);
        Specification<EventSeat> spec = EventSpecifications.seatsOf(eventId)
                .and(EventSpecifications.seatStatus(status))
                .and(EventSpecifications.seatSection(section));
        Pageable bySeat = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by("section.name", "seat.rowLabel", "seat.seatNumber"));
        return PageResponse.of(eventSeats.findAll(spec, bySeat), es -> new EventSeatResponse(es.getId(),
                es.getSeat().getId(), es.getSection().getName(), es.getSeat().getRowLabel(),
                es.getSeat().getSeatNumber(), es.getPricePaise(), es.getStatus()));
    }

    /** Drafts are visible only to their organiser and admins; to everyone else they do not exist. */
    private Event findVisible(Long eventId, CurrentUser caller) {
        Event event = find(eventId);
        if (event.getStatus() == EventStatus.DRAFT && !canManage(event, caller)) {
            throw notFound(eventId);
        }
        return event;
    }

    private Event findManageable(Long eventId, CurrentUser caller) {
        Event event = find(eventId);
        if (!canManage(event, caller)) {
            throw new ForbiddenException("Only the event's organiser or an admin can change it.");
        }
        return event;
    }

    static boolean canManage(Event event, CurrentUser caller) {
        return caller != null && (caller.isAdmin() || event.isOwnedBy(caller.id()));
    }

    private Event find(Long eventId) {
        return events.findById(eventId).orElseThrow(() -> notFound(eventId));
    }

    private static NotFoundException notFound(Long eventId) {
        return new NotFoundException("Event " + eventId + " not found.");
    }

    private Map<String, Section> sectionsByName(Long venueId) {
        return sections.findByVenueIdOrderByName(venueId).stream()
                .collect(Collectors.toMap(Section::getName, Function.identity()));
    }

    /** Every venue section needs a price, and every price must name a real section. */
    static void checkPricesCoverSections(Set<String> venueSections, Set<String> pricedSections) {
        if (venueSections.isEmpty()) {
            throw new BadRequestException("The venue has no sections yet. Add seats before creating an event.");
        }
        Set<String> missing = new TreeSet<>(venueSections);
        missing.removeAll(pricedSections);
        if (!missing.isEmpty()) {
            throw new BadRequestException("Every section needs a price. Missing: " + String.join(", ", missing) + ".");
        }
        Set<String> unknown = new TreeSet<>(pricedSections);
        unknown.removeAll(venueSections);
        if (!unknown.isEmpty()) {
            throw new BadRequestException("Unknown sections for this venue: " + String.join(", ", unknown) + ".");
        }
    }

    private EventDetailResponse detail(Event event) {
        List<SectionPrice> sectionPrices = prices.findByEventIdOrderBySectionName(event.getId()).stream()
                .map(p -> new SectionPrice(p.getSection().getId(), p.getSection().getName(), p.getPricePaise()))
                .toList();
        Map<EventSeatStatus, Long> counts = new EnumMap<>(EventSeatStatus.class);
        eventSeats.countByStatus(event.getId()).forEach(c -> counts.put(c.getStatus(), c.getSeats()));
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        Venue venue = event.getVenue();
        return new EventDetailResponse(event.getId(), event.getTitle(), event.getStartsAt(), event.getStatus(),
                new VenueSummary(venue.getId(), venue.getName(), venue.getCity()), event.getOrganiser().getId(),
                event.getCreatedAt(), event.getPublishedAt(), event.getCancelledAt(), sectionPrices, total, counts);
    }

    private static EventResponse summary(Event event) {
        Venue venue = event.getVenue();
        return new EventResponse(event.getId(), event.getTitle(), event.getStartsAt(), event.getStatus(),
                new VenueSummary(venue.getId(), venue.getName(), venue.getCity()), event.getOrganiser().getId());
    }
}
