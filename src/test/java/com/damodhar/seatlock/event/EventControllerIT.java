package com.damodhar.seatlock.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.damodhar.seatlock.AbstractIntegrationTest;
import com.damodhar.seatlock.user.Role;
import com.fasterxml.jackson.databind.ObjectMapper;

class EventControllerIT extends AbstractIntegrationTest {

    static final Instant NEXT_MONTH = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    EventSeatRepository eventSeatRepository;

    String organiser;
    String city;
    long venueId;

    /** A fresh organiser and a venue in its own city: Stalls 3 x 4 seats, Balcony 2 x 3 seats. */
    @BeforeEach
    void setUp() throws Exception {
        organiser = bearer(Role.ORGANISER);
        city = "City-" + UUID.randomUUID();
        venueId = createVenue(city, Map.of("Stalls", List.of("A", "B", "C"), "Balcony", List.of("A", "B")),
                Map.of("Stalls", 4, "Balcony", 3));
    }

    // ---- create ---------------------------------------------------------------------------

    @Test
    void organiserCreatesDraftEvent() throws Exception {
        String organiserId = me(organiser);

        createEvent(organiser, body(venueId, "Ghazal Night", NEXT_MONTH, prices(49_900, 29_900)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/v1/events/")))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.title").value("Ghazal Night"))
                .andExpect(jsonPath("$.organiserId").value(Long.valueOf(organiserId)))
                .andExpect(jsonPath("$.venue.city").value(city))
                .andExpect(jsonPath("$.sectionPrices[0].section").value("Balcony"))
                .andExpect(jsonPath("$.sectionPrices[0].pricePaise").value(29_900))
                .andExpect(jsonPath("$.sectionPrices[1].section").value("Stalls"))
                .andExpect(jsonPath("$.sectionPrices[1].pricePaise").value(49_900))
                .andExpect(jsonPath("$.totalSeats").value(0));
    }

    @Test
    void buyerGets403AndAnonymousGets401OnCreate() throws Exception {
        createEvent(bearer(Role.BUYER), body(venueId, "Nope", NEXT_MONTH, prices(1, 1)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON)
                        .content(body(venueId, "Nope", NEXT_MONTH, prices(1, 1))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void startsAtInThePastIs400() throws Exception {
        createEvent(organiser, body(venueId, "Yesterday", Instant.now().minus(1, ChronoUnit.DAYS), prices(1, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("startsAt must be in the future."));
    }

    @Test
    void missingSectionPriceIs400() throws Exception {
        createEvent(organiser, body(venueId, "Half priced", NEXT_MONTH, Map.of("Stalls", 100L)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Every section needs a price. Missing: Balcony."));
    }

    @Test
    void unknownSectionPriceIs400() throws Exception {
        Map<String, Long> withExtra = new LinkedHashMap<>(prices(1, 1));
        withExtra.put("Mezzanine", 5L);

        createEvent(organiser, body(venueId, "Extra", NEXT_MONTH, withExtra))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unknown sections for this venue: Mezzanine."));
    }

    @Test
    void venueWithoutSeatsIs400() throws Exception {
        long empty = createVenue(city, Map.of(), Map.of());

        createEvent(organiser, body(empty, "Empty", NEXT_MONTH, Map.of("Stalls", 1L)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownVenueIs404() throws Exception {
        createEvent(organiser, body(999_999L, "Nowhere", NEXT_MONTH, prices(1, 1)))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidBodyIs400WithFieldErrors() throws Exception {
        createEvent(organiser, body(null, "", null, Map.of("Stalls", -1L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.venueId").exists())
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors.startsAt").exists())
                .andExpect(jsonPath("$.errors['sectionPrices[Stalls]']").exists());
    }

    // ---- publish --------------------------------------------------------------------------

    @Test
    void publishGeneratesOneEventSeatPerVenueSeatWithSectionPrice() throws Exception {
        long eventId = draftEvent(organiser);

        perform(post("/api/v1/events/" + eventId + "/publish"), organiser)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").exists())
                .andExpect(jsonPath("$.totalSeats").value(18))
                .andExpect(jsonPath("$.seatCounts.AVAILABLE").value(18));

        assertThat(eventSeatRepository.countByEventId(eventId)).isEqualTo(18);
        mockMvc.perform(get("/api/v1/events/" + eventId + "/seats").param("section", "Stalls").param("size", "1"))
                .andExpect(jsonPath("$.totalElements").value(12))
                .andExpect(jsonPath("$.content[0].pricePaise").value(49_900));
        mockMvc.perform(get("/api/v1/events/" + eventId + "/seats").param("section", "Balcony").param("size", "1"))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.content[0].pricePaise").value(29_900));
    }

    @Test
    void publishingTwiceIs409AndCreatesNoExtraSeats() throws Exception {
        long eventId = draftEvent(organiser);
        perform(post("/api/v1/events/" + eventId + "/publish"), organiser).andExpect(status().isOk());

        perform(post("/api/v1/events/" + eventId + "/publish"), organiser)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Event " + eventId + " is PUBLISHED and cannot become PUBLISHED."));
        assertThat(eventSeatRepository.countByEventId(eventId)).isEqualTo(18);
    }

    @Test
    void anotherOrganiserGets403AndAdminCanPublish() throws Exception {
        long eventId = draftEvent(organiser);

        perform(post("/api/v1/events/" + eventId + "/publish"), bearer(Role.ORGANISER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://seatlock.dev/problems/forbidden"));
        perform(post("/api/v1/events/" + eventId + "/publish"), bearer(Role.BUYER))
                .andExpect(status().isForbidden());
        perform(post("/api/v1/events/" + eventId + "/publish"), bearer(Role.ADMIN))
                .andExpect(status().isOk());
    }

    @Test
    void publishUnknownEventIs404() throws Exception {
        perform(post("/api/v1/events/999999/publish"), organiser).andExpect(status().isNotFound());
    }

    @Test
    void sectionAddedAfterCreationBlocksPublishUntilPriced() throws Exception {
        long eventId = draftEvent(organiser);
        perform(post("/api/v1/venues/" + venueId + "/seats"), organiser,
                "{\"section\":\"Box\",\"rowLabels\":[\"A\"],\"seatsPerRow\":2}")
                .andExpect(status().isCreated());

        perform(post("/api/v1/events/" + eventId + "/publish"), organiser)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Every section needs a price. Missing: Box."));
        assertThat(eventSeatRepository.countByEventId(eventId)).isZero();
    }

    @Test
    void publishingATwoThousandSeatVenueCreatesTwoThousandEventSeats() throws Exception {
        List<String> rows = IntStream.range(0, 20).mapToObj(i -> String.valueOf((char) ('A' + i))).toList();
        long bigVenue = createVenue(city, Map.of("Arena", rows), Map.of("Arena", 100));
        long eventId = idOf(createEvent(organiser, body(bigVenue, "Stadium Show", NEXT_MONTH, Map.of("Arena", 99_900L)))
                .andExpect(status().isCreated()));

        perform(post("/api/v1/events/" + eventId + "/publish"), organiser)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSeats").value(2_000));

        assertThat(eventSeatRepository.countByEventId(eventId)).isEqualTo(2_000);
        mockMvc.perform(get("/api/v1/events/" + eventId + "/seats").param("status", "AVAILABLE"))
                .andExpect(jsonPath("$.totalElements").value(2_000));
    }

    // ---- cancel ---------------------------------------------------------------------------

    @Test
    void draftAndPublishedEventsCanBeCancelledOnce() throws Exception {
        long draft = draftEvent(organiser);
        long published = publishedEvent(organiser);

        perform(post("/api/v1/events/" + draft + "/cancel"), organiser)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").exists());
        perform(post("/api/v1/events/" + published + "/cancel"), organiser)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        perform(post("/api/v1/events/" + published + "/cancel"), organiser)
                .andExpect(status().isConflict());
        perform(post("/api/v1/events/" + draft + "/publish"), organiser)
                .andExpect(status().isConflict());
    }

    @Test
    void anotherOrganiserCannotCancel() throws Exception {
        long eventId = publishedEvent(organiser);

        perform(post("/api/v1/events/" + eventId + "/cancel"), bearer(Role.ORGANISER))
                .andExpect(status().isForbidden());
    }

    // ---- list -----------------------------------------------------------------------------

    @Test
    void listShowsOnlyPublishedEventsInCitySortedByStart() throws Exception {
        long later = publishedEvent(organiser, "Later", NEXT_MONTH.plus(2, ChronoUnit.DAYS));
        long sooner = publishedEvent(organiser, "Sooner", NEXT_MONTH);
        draftEvent(organiser);
        long cancelled = publishedEvent(organiser, "Cancelled", NEXT_MONTH.plus(1, ChronoUnit.DAYS));
        perform(post("/api/v1/events/" + cancelled + "/cancel"), organiser).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/events").param("city", city.toUpperCase()).param("sort", "title,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(sooner))
                .andExpect(jsonPath("$.content[1].id").value(later))
                .andExpect(jsonPath("$.content[0].status").value("PUBLISHED"))
                .andExpect(jsonPath("$.content[0].venue.city").value(city));
    }

    @Test
    void listFiltersByDateRangeAndPaginates() throws Exception {
        publishedEvent(organiser, "Day 1", NEXT_MONTH);
        long day3 = publishedEvent(organiser, "Day 3", NEXT_MONTH.plus(2, ChronoUnit.DAYS));
        publishedEvent(organiser, "Day 5", NEXT_MONTH.plus(4, ChronoUnit.DAYS));

        mockMvc.perform(get("/api/v1/events").param("city", city)
                        .param("from", NEXT_MONTH.plus(1, ChronoUnit.DAYS).toString())
                        .param("to", NEXT_MONTH.plus(3, ChronoUnit.DAYS).toString()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(day3));

        mockMvc.perform(get("/api/v1/events").param("city", city).param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Day 5"));
    }

    @Test
    void fromAfterToIs400() throws Exception {
        mockMvc.perform(get("/api/v1/events")
                        .param("from", NEXT_MONTH.toString())
                        .param("to", NEXT_MONTH.minus(1, ChronoUnit.DAYS).toString()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/events").param("from", "next tuesday"))
                .andExpect(status().isBadRequest());
    }

    // ---- get ------------------------------------------------------------------------------

    @Test
    void publishedAndCancelledEventsArePublicDraftsAreNot() throws Exception {
        long published = publishedEvent(organiser);
        long draft = draftEvent(organiser);

        mockMvc.perform(get("/api/v1/events/" + published))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seatCounts.AVAILABLE").value(18));
        mockMvc.perform(get("/api/v1/events/" + draft))
                .andExpect(status().isNotFound());
        perform(get("/api/v1/events/" + draft), bearer(Role.ORGANISER))
                .andExpect(status().isNotFound());
        perform(get("/api/v1/events/" + draft), organiser)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
        perform(get("/api/v1/events/" + draft), bearer(Role.ADMIN))
                .andExpect(status().isOk());

        perform(post("/api/v1/events/" + published + "/cancel"), organiser).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/events/" + published))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void unknownEventIs404() throws Exception {
        mockMvc.perform(get("/api/v1/events/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Event 999999 not found."));
        mockMvc.perform(get("/api/v1/events/999999/seats"))
                .andExpect(status().isNotFound());
    }

    // ---- seats ----------------------------------------------------------------------------

    @Test
    void seatsAreFilteredByStatusAndSectionAndOrdered() throws Exception {
        long eventId = publishedEvent(organiser);

        mockMvc.perform(get("/api/v1/events/" + eventId + "/seats").param("status", "AVAILABLE").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(18))
                .andExpect(jsonPath("$.totalPages").value(4))
                .andExpect(jsonPath("$.content[0].section").value("Balcony"))
                .andExpect(jsonPath("$.content[0].row").value("A"))
                .andExpect(jsonPath("$.content[0].number").value(1))
                .andExpect(jsonPath("$.content[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.content[1].number").value(2));
        mockMvc.perform(get("/api/v1/events/" + eventId + "/seats").param("status", "SOLD"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/v1/events/" + eventId + "/seats").param("section", "Nope"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void invalidSeatStatusIs400() throws Exception {
        long eventId = publishedEvent(organiser);

        mockMvc.perform(get("/api/v1/events/" + eventId + "/seats").param("status", "FREE"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void draftSeatsAreHiddenFromThePublic() throws Exception {
        long eventId = draftEvent(organiser);

        mockMvc.perform(get("/api/v1/events/" + eventId + "/seats")).andExpect(status().isNotFound());
        perform(get("/api/v1/events/" + eventId + "/seats"), organiser)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // ---- helpers --------------------------------------------------------------------------

    static Map<String, Long> prices(long stalls, long balcony) {
        return Map.of("Stalls", stalls, "Balcony", balcony);
    }

    long draftEvent(String token) throws Exception {
        return idOf(createEvent(token, body(venueId, "Draft " + UUID.randomUUID(), NEXT_MONTH, prices(49_900, 29_900)))
                .andExpect(status().isCreated()));
    }

    long publishedEvent(String token) throws Exception {
        return publishedEvent(token, "Show " + UUID.randomUUID(), NEXT_MONTH);
    }

    long publishedEvent(String token, String title, Instant startsAt) throws Exception {
        long id = idOf(createEvent(token, body(venueId, title, startsAt, prices(49_900, 29_900)))
                .andExpect(status().isCreated()));
        perform(post("/api/v1/events/" + id + "/publish"), token).andExpect(status().isOk());
        return id;
    }

    long createVenue(String venueCity, Map<String, List<String>> rows, Map<String, Integer> seatsPerRow)
            throws Exception {
        long id = idOf(perform(post("/api/v1/venues"), organiser,
                json.writeValueAsString(Map.of("name", "Venue " + UUID.randomUUID(), "city", venueCity)))
                .andExpect(status().isCreated()));
        for (var section : rows.entrySet()) {
            perform(post("/api/v1/venues/" + id + "/seats"), organiser, json.writeValueAsString(Map.of(
                    "section", section.getKey(), "rowLabels", section.getValue(),
                    "seatsPerRow", seatsPerRow.get(section.getKey()))))
                    .andExpect(status().isCreated());
        }
        return id;
    }

    String body(Long venue, String title, Instant startsAt, Map<String, Long> sectionPrices) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("venueId", venue);
        body.put("title", title);
        body.put("startsAt", startsAt == null ? null : startsAt.toString());
        body.put("sectionPrices", sectionPrices);
        return json.writeValueAsString(body);
    }

    ResultActions createEvent(String token, String body) throws Exception {
        return perform(post("/api/v1/events"), token, body);
    }

    ResultActions perform(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header("Authorization", token));
    }

    ResultActions perform(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        return mockMvc.perform(request.header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    String me(String token) throws Exception {
        String body = perform(get("/api/v1/users/me"), token).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asText();
    }

    long idOf(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }
}
