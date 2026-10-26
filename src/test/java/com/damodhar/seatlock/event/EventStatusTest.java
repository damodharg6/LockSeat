package com.damodhar.seatlock.event;

import static com.damodhar.seatlock.event.EventStatus.CANCELLED;
import static com.damodhar.seatlock.event.EventStatus.DRAFT;
import static com.damodhar.seatlock.event.EventStatus.PUBLISHED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.damodhar.seatlock.common.error.ConflictException;
import com.damodhar.seatlock.user.Role;
import com.damodhar.seatlock.user.User;
import com.damodhar.seatlock.venue.Venue;

class EventStatusTest {

    static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");

    @ParameterizedTest(name = "{0} -> {1} allowed: {2}")
    @CsvSource({
            "DRAFT,     PUBLISHED, true",
            "DRAFT,     CANCELLED, true",
            "DRAFT,     DRAFT,     false",
            "PUBLISHED, CANCELLED, true",
            "PUBLISHED, DRAFT,     false",
            "PUBLISHED, PUBLISHED, false",
            "CANCELLED, DRAFT,     false",
            "CANCELLED, PUBLISHED, false",
            "CANCELLED, CANCELLED, false"
    })
    void transitions(EventStatus from, EventStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }

    @Test
    void newEventIsDraft() {
        assertThat(event().getStatus()).isEqualTo(DRAFT);
    }

    @Test
    void draftPublishesThenCancels() {
        Event event = event();

        event.publish(NOW);
        assertThat(event.getStatus()).isEqualTo(PUBLISHED);
        assertThat(event.getPublishedAt()).isEqualTo(NOW);

        event.cancel(NOW.plusSeconds(60));
        assertThat(event.getStatus()).isEqualTo(CANCELLED);
        assertThat(event.getCancelledAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void draftCanBeCancelledDirectly() {
        Event event = event();

        event.cancel(NOW);

        assertThat(event.getStatus()).isEqualTo(CANCELLED);
        assertThat(event.getPublishedAt()).isNull();
    }

    @Test
    void publishingTwiceIsConflict() {
        Event event = event();
        event.publish(NOW);

        assertThatThrownBy(() -> event.publish(NOW))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("PUBLISHED and cannot become PUBLISHED");
        assertThat(event.getPublishedAt()).isEqualTo(NOW);
    }

    @Test
    void cancelledEventCannotBePublishedOrCancelledAgain() {
        Event event = event();
        event.cancel(NOW);

        assertThatThrownBy(() -> event.publish(NOW)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> event.cancel(NOW)).isInstanceOf(ConflictException.class);
        assertThat(event.getStatus()).isEqualTo(CANCELLED);
    }

    @Test
    void ownershipIsByOrganiserId() {
        Event event = event();

        assertThat(event.isOwnedBy(7L)).isTrue();
        assertThat(event.isOwnedBy(8L)).isFalse();
    }

    static Event event() {
        User organiser = new User("org@example.com", "hash", "Org", Role.ORGANISER);
        ReflectionTestUtils.setField(organiser, "id", 7L);
        return new Event(new Venue("Hall", "Pune"), organiser, "Concert", NOW.plusSeconds(86_400));
    }
}
