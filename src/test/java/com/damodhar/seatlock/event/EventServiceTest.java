package com.damodhar.seatlock.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import com.damodhar.seatlock.auth.CurrentUser;
import com.damodhar.seatlock.common.error.BadRequestException;
import com.damodhar.seatlock.common.error.ConflictException;
import com.damodhar.seatlock.common.error.ForbiddenException;
import com.damodhar.seatlock.common.error.NotFoundException;
import com.damodhar.seatlock.event.dto.CreateEventRequest;
import com.damodhar.seatlock.event.dto.EventSearch;
import com.damodhar.seatlock.user.Role;
import com.damodhar.seatlock.user.UserRepository;
import com.damodhar.seatlock.venue.SectionRepository;
import com.damodhar.seatlock.venue.VenueRepository;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    static final Instant NOW = EventStatusTest.NOW;
    static final CurrentUser OWNER = new CurrentUser(7L, Role.ORGANISER);
    static final CurrentUser OTHER_ORGANISER = new CurrentUser(8L, Role.ORGANISER);
    static final CurrentUser ADMIN = new CurrentUser(1L, Role.ADMIN);

    @Mock
    EventRepository events;
    @Mock
    EventSectionPriceRepository prices;
    @Mock
    EventSeatRepository eventSeats;
    @Mock
    VenueRepository venues;
    @Mock
    SectionRepository sections;
    @Mock
    UserRepository users;

    EventService service;

    @BeforeEach
    void setUp() {
        service = new EventService(events, prices, eventSeats, venues, sections, users,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void startsAtInThePastIsRejectedBeforeAnyLookup() {
        var request = new CreateEventRequest(1L, "Concert", NOW.minusSeconds(1), Map.of("A", 100L));

        assertThatThrownBy(() -> service.create(request, OWNER))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("startsAt must be in the future.");
        verify(venues, never()).findById(any());
    }

    @Test
    void unknownVenueIsNotFound() {
        when(venues.findById(9L)).thenReturn(Optional.empty());
        var request = new CreateEventRequest(9L, "Concert", NOW.plusSeconds(60), Map.of("A", 100L));

        assertThatThrownBy(() -> service.create(request, OWNER)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void everySectionNeedsAPrice() {
        assertThatThrownBy(() -> EventService.checkPricesCoverSections(Set.of("A", "B", "C"), Set.of("A")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Every section needs a price. Missing: B, C.");
    }

    @Test
    void pricesForUnknownSectionsAreRejected() {
        assertThatThrownBy(() -> EventService.checkPricesCoverSections(Set.of("A"), Set.of("A", "Z")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Unknown sections for this venue: Z.");
    }

    @Test
    void venueWithoutSectionsIsRejected() {
        assertThatThrownBy(() -> EventService.checkPricesCoverSections(Set.of(), Set.of("A")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("no sections");
    }

    @Test
    void exactSectionMatchIsAccepted() {
        assertThatCode(() -> EventService.checkPricesCoverSections(Set.of("A", "B"), Set.of("B", "A")))
                .doesNotThrowAnyException();
    }

    @Test
    void onlyOwnerOrAdminCanManage() {
        Event event = EventStatusTest.event();

        assertThat(EventService.canManage(event, OWNER)).isTrue();
        assertThat(EventService.canManage(event, ADMIN)).isTrue();
        assertThat(EventService.canManage(event, OTHER_ORGANISER)).isFalse();
        assertThat(EventService.canManage(event, null)).isFalse();
    }

    @Test
    void otherOrganiserCannotPublish() {
        Event event = withId(EventStatusTest.event(), 3L);
        when(events.findById(3L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service.publish(3L, OTHER_ORGANISER)).isInstanceOf(ForbiddenException.class);
        assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
        verify(eventSeats, never()).generateForEvent(any());
    }

    @Test
    void publishingAPublishedEventIsConflictAndGeneratesNothing() {
        Event event = withId(EventStatusTest.event(), 3L);
        event.publish(NOW);
        when(events.findById(3L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service.publish(3L, OWNER)).isInstanceOf(ConflictException.class);
        verify(eventSeats, never()).generateForEvent(any());
    }

    @Test
    void eventThatAlreadyStartedCannotBePublished() {
        Event event = withId(EventStatusTest.event(), 3L);
        ReflectionTestUtils.setField(event, "startsAt", NOW.minusSeconds(1));
        when(events.findById(3L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service.publish(3L, OWNER))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already started");
    }

    @Test
    void cancellingACancelledEventIsConflict() {
        Event event = withId(EventStatusTest.event(), 3L);
        event.cancel(NOW);
        when(events.findById(3L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service.cancel(3L, OWNER)).isInstanceOf(ConflictException.class);
    }

    @Test
    void draftIsHiddenFromEveryoneButOwnerAndAdmin() {
        Event event = withId(EventStatusTest.event(), 3L);
        when(events.findById(3L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service.get(3L, null)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.get(3L, OTHER_ORGANISER)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void fromAfterToIsRejected() {
        var search = new EventSearch(null, NOW.plusSeconds(10), NOW);

        assertThatThrownBy(() -> service.listPublished(search, Pageable.ofSize(10)))
                .isInstanceOf(BadRequestException.class);
    }

    static Event withId(Event event, Long id) {
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }
}
