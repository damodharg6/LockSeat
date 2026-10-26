package com.damodhar.seatlock.event;

import java.time.Instant;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.damodhar.seatlock.common.error.ConflictException;
import com.damodhar.seatlock.user.User;
import com.damodhar.seatlock.venue.Venue;

@Entity
@Table(name = "event")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id")
    private Venue venue;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organiser_id")
    private User organiser;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    /** Two concurrent publish or cancel requests: the second one's update fails instead of overwriting. */
    @Version
    private long version;

    protected Event() {
    }

    public Event(Venue venue, User organiser, String title, Instant startsAt) {
        this.venue = venue;
        this.organiser = organiser;
        this.title = title;
        this.startsAt = startsAt;
        this.status = EventStatus.DRAFT;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void publish(Instant now) {
        moveTo(EventStatus.PUBLISHED);
        publishedAt = now;
    }

    public void cancel(Instant now) {
        moveTo(EventStatus.CANCELLED);
        cancelledAt = now;
    }

    /** Throws 409 if the event cannot move to {@code target} from its current status. */
    public void checkCanMoveTo(EventStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new ConflictException("Event " + id + " is " + status + " and cannot become " + target + ".");
        }
    }

    private void moveTo(EventStatus target) {
        checkCanMoveTo(target);
        status = target;
    }

    public boolean isOwnedBy(Long userId) {
        return organiser.getId().equals(userId);
    }

    public Long getId() {
        return id;
    }

    public Venue getVenue() {
        return venue;
    }

    public User getOrganiser() {
        return organiser;
    }

    public String getTitle() {
        return title;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public EventStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }
}
