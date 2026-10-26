package com.damodhar.seatlock.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.damodhar.seatlock.venue.Seat;
import com.damodhar.seatlock.venue.Section;

/**
 * One venue seat for one event. Rows are created in bulk by {@link EventSeatRepository#generateForEvent}
 * when the event is published, never one by one through JPA.
 */
@Entity
@Table(name = "event_seat")
public class EventSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "event_seat_seq")
    @SequenceGenerator(name = "event_seat_seq", sequenceName = "event_seat_seq", allocationSize = 50)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id")
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id")
    private Seat seat;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id")
    private Section section;

    @Column(name = "price_paise", nullable = false)
    private long pricePaise;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventSeatStatus status;

    /** Optimistic lock for seat state changes from M4 onwards (hold, confirm, release). */
    @Version
    private long version;

    protected EventSeat() {
    }

    public Long getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    public Seat getSeat() {
        return seat;
    }

    public Section getSection() {
        return section;
    }

    public long getPricePaise() {
        return pricePaise;
    }

    public EventSeatStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }
}
