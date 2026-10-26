package com.damodhar.seatlock.venue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.damodhar.seatlock.common.error.BadRequestException;
import com.damodhar.seatlock.common.error.ConflictException;
import com.damodhar.seatlock.common.error.NotFoundException;
import com.damodhar.seatlock.common.web.PageResponse;
import com.damodhar.seatlock.venue.dto.AddSeatsRequest;
import com.damodhar.seatlock.venue.dto.AddSeatsResponse;
import com.damodhar.seatlock.venue.dto.CreateVenueRequest;
import com.damodhar.seatlock.venue.dto.SeatResponse;
import com.damodhar.seatlock.venue.dto.VenueDetailResponse;
import com.damodhar.seatlock.venue.dto.VenueDetailResponse.RowSummary;
import com.damodhar.seatlock.venue.dto.VenueDetailResponse.SectionSummary;
import com.damodhar.seatlock.venue.dto.VenueResponse;

@Service
public class VenueService {

    /** Upper bound for one layout request, so a single call cannot insert millions of rows. */
    static final int MAX_SEATS_PER_REQUEST = 10_000;

    /** Rows sort A..Z, then AA..AZ, like printed seat maps. */
    static final Comparator<String> ROW_ORDER = Comparator.comparingInt(String::length)
            .thenComparing(Comparator.naturalOrder());

    private final VenueRepository venues;
    private final SectionRepository sections;
    private final SeatRepository seats;

    public VenueService(VenueRepository venues, SectionRepository sections, SeatRepository seats) {
        this.venues = venues;
        this.sections = sections;
        this.seats = seats;
    }

    @Transactional
    public VenueResponse create(CreateVenueRequest request) {
        Venue venue = venues.save(new Venue(request.name().strip(), request.city().strip()));
        return toResponse(venue);
    }

    @Transactional(readOnly = true)
    public PageResponse<VenueResponse> list(Pageable pageable) {
        return PageResponse.of(venues.findAll(pageable), VenueService::toResponse);
    }

    @Transactional(readOnly = true)
    public VenueDetailResponse get(Long venueId) {
        Venue venue = findVenue(venueId);
        Map<Long, List<RowCount>> rowsBySection = seats.countRowsByVenue(venueId).stream()
                .collect(Collectors.groupingBy(RowCount::sectionId));

        List<SectionSummary> summaries = new ArrayList<>();
        long totalSeats = 0;
        for (Section section : sections.findByVenueIdOrderByName(venueId)) {
            List<RowSummary> rows = rowsBySection.getOrDefault(section.getId(), List.of()).stream()
                    .sorted(Comparator.comparing(RowCount::rowLabel, ROW_ORDER))
                    .map(r -> new RowSummary(r.rowLabel(), r.seats()))
                    .toList();
            long seatCount = rows.stream().mapToLong(RowSummary::seats).sum();
            totalSeats += seatCount;
            summaries.add(new SectionSummary(section.getId(), section.getName(), seatCount, rows));
        }
        return new VenueDetailResponse(venue.getId(), venue.getName(), venue.getCity(), venue.getCreatedAt(),
                totalSeats, summaries);
    }

    /**
     * Adds a block of seats to a section. Rows that already exist in the section are rejected as a
     * whole (409) rather than partially filled; the unique constraint on seat position backs this
     * up if two requests race.
     */
    @Transactional
    public AddSeatsResponse addSeats(Long venueId, AddSeatsRequest request) {
        Venue venue = findVenue(venueId);
        List<String> rowLabels = request.rowLabels();
        if (new HashSet<>(rowLabels).size() != rowLabels.size()) {
            throw new BadRequestException("Row labels must be unique.");
        }
        int total = rowLabels.size() * request.seatsPerRow();
        if (total > MAX_SEATS_PER_REQUEST) {
            throw new BadRequestException("A layout may create at most " + MAX_SEATS_PER_REQUEST
                    + " seats per request, this one would create " + total + ".");
        }

        String sectionName = request.section().strip();
        Section section = sections.findByVenueIdAndName(venueId, sectionName)
                .orElseGet(() -> sections.save(new Section(venue, sectionName)));
        if (section.getId() != null && seats.existsBySectionIdAndRowLabelIn(section.getId(), rowLabels)) {
            throw new ConflictException("Section '" + sectionName + "' already has seats in one of these rows.");
        }

        List<Seat> block = new ArrayList<>(total);
        for (String row : rowLabels) {
            for (int number = 1; number <= request.seatsPerRow(); number++) {
                block.add(new Seat(section, row, number));
            }
        }
        seats.saveAll(block);
        return new AddSeatsResponse(section.getId(), section.getName(), total);
    }

    @Transactional(readOnly = true)
    public PageResponse<SeatResponse> listSeats(Long venueId, Pageable pageable) {
        findVenue(venueId);
        return PageResponse.of(seats.findBySectionVenueId(venueId, pageable),
                s -> new SeatResponse(s.getId(), s.getSection().getId(), s.getSection().getName(),
                        s.getRowLabel(), s.getSeatNumber()));
    }

    private Venue findVenue(Long venueId) {
        return venues.findById(venueId)
                .orElseThrow(() -> new NotFoundException("Venue " + venueId + " not found."));
    }

    private static VenueResponse toResponse(Venue venue) {
        return new VenueResponse(venue.getId(), venue.getName(), venue.getCity(), venue.getCreatedAt());
    }
}
