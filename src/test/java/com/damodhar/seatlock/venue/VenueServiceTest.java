package com.damodhar.seatlock.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.damodhar.seatlock.common.error.BadRequestException;
import com.damodhar.seatlock.common.error.ConflictException;
import com.damodhar.seatlock.common.error.NotFoundException;
import com.damodhar.seatlock.venue.dto.AddSeatsRequest;
import com.damodhar.seatlock.venue.dto.CreateVenueRequest;

@ExtendWith(MockitoExtension.class)
class VenueServiceTest {

    @Mock
    VenueRepository venues;
    @Mock
    SectionRepository sections;
    @Mock
    SeatRepository seats;

    @InjectMocks
    VenueService service;

    @Test
    void createTrimsNameAndCity() {
        when(venues.save(any(Venue.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.create(new CreateVenueRequest("  Grand Hall ", " Pune "));

        assertThat(response.name()).isEqualTo("Grand Hall");
        assertThat(response.city()).isEqualTo("Pune");
    }

    @Test
    void getUnknownVenueIsNotFound() {
        when(venues.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(9L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Venue 9");
    }

    @Test
    void addSeatsToUnknownVenueIsNotFound() {
        when(venues.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addSeats(9L, new AddSeatsRequest("Stalls", List.of("A"), 10)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void addSeatsCreatesSectionAndEverySeatInTheBlock() {
        Venue venue = venue(1L);
        when(venues.findById(1L)).thenReturn(Optional.of(venue));
        when(sections.findByVenueIdAndName(1L, "Stalls")).thenReturn(Optional.empty());
        when(sections.save(any(Section.class))).thenAnswer(inv -> withId(inv.getArgument(0), 5L));
        when(seats.existsBySectionIdAndRowLabelIn(eq(5L), anyCollection())).thenReturn(false);

        var response = service.addSeats(1L, new AddSeatsRequest("Stalls", List.of("A", "B", "C"), 4));

        assertThat(response.seatsCreated()).isEqualTo(12);
        assertThat(response.sectionId()).isEqualTo(5L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Seat>> saved = ArgumentCaptor.forClass(List.class);
        verify(seats).saveAll(saved.capture());
        assertThat(saved.getValue()).hasSize(12)
                .extracting(s -> s.getRowLabel() + s.getSeatNumber())
                .contains("A1", "A4", "C1", "C4");
    }

    @Test
    void addSeatsToExistingRowIsConflict() {
        Venue venue = venue(1L);
        Section stalls = withId(new Section(venue, "Stalls"), 5L);
        when(venues.findById(1L)).thenReturn(Optional.of(venue));
        when(sections.findByVenueIdAndName(1L, "Stalls")).thenReturn(Optional.of(stalls));
        when(seats.existsBySectionIdAndRowLabelIn(5L, List.of("A"))).thenReturn(true);

        assertThatThrownBy(() -> service.addSeats(1L, new AddSeatsRequest("Stalls", List.of("A"), 10)))
                .isInstanceOf(ConflictException.class);
        verify(seats, never()).saveAll(anyIterable());
    }

    @Test
    void duplicateRowLabelsAreRejected() {
        when(venues.findById(1L)).thenReturn(Optional.of(venue(1L)));

        assertThatThrownBy(() -> service.addSeats(1L, new AddSeatsRequest("Stalls", List.of("A", "A"), 10)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("unique");
    }

    @Test
    void layoutAboveTheSeatLimitIsRejected() {
        when(venues.findById(1L)).thenReturn(Optional.of(venue(1L)));
        List<String> rows = IntStream.range(0, 51)
                .mapToObj(i -> i < 26 ? String.valueOf((char) ('A' + i)) : "A" + (char) ('A' + i - 26))
                .toList();
        // 51 rows x 200 seats = 10,200 > 10,000

        assertThatThrownBy(() -> service.addSeats(1L, new AddSeatsRequest("Stalls", rows, 200)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("at most");
    }

    @Test
    void rowsSortLikeAPrintedSeatMap() {
        assertThat(List.of("AA", "B", "A", "Z", "AB").stream().sorted(VenueService.ROW_ORDER).toList())
                .containsExactly("A", "B", "Z", "AA", "AB");
    }

    private static Venue venue(Long id) {
        return withId(new Venue("Grand Hall", "Pune"), id);
    }

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
