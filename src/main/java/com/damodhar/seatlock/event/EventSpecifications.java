package com.damodhar.seatlock.event;

import java.time.Instant;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

/** Optional filters; each returns null (no restriction) when its argument is null or blank. */
final class EventSpecifications {

    private EventSpecifications() {
    }

    static Specification<Event> published() {
        return (root, query, cb) -> cb.equal(root.get("status"), EventStatus.PUBLISHED);
    }

    static Specification<Event> inCity(String city) {
        if (city == null || city.isBlank()) {
            return null;
        }
        String normalised = city.strip().toLowerCase(Locale.ROOT);
        return (root, query, cb) -> cb.equal(cb.lower(root.join("venue").get("city")), normalised);
    }

    static Specification<Event> startsFrom(Instant from) {
        return from == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("startsAt"), from);
    }

    static Specification<Event> startsBefore(Instant to) {
        return to == null ? null : (root, query, cb) -> cb.lessThan(root.get("startsAt"), to);
    }

    static Specification<EventSeat> seatsOf(Long eventId) {
        return (root, query, cb) -> cb.equal(root.get("event").get("id"), eventId);
    }

    static Specification<EventSeat> seatStatus(EventSeatStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    static Specification<EventSeat> seatSection(String section) {
        if (section == null || section.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("section").get("name"), section.strip());
    }
}
