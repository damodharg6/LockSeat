package com.damodhar.seatlock.venue;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.damodhar.seatlock.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class VenueControllerIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper json;
    @Test
    void createVenueReturns201WithLocation() throws Exception {
        postJson("/api/v1/venues", "{\"name\":\"Shanmukhananda Hall\",\"city\":\"Mumbai\"}")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/v1/venues/")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Shanmukhananda Hall"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void invalidVenueIs400WithFieldErrors() throws Exception {
        postJson("/api/v1/venues", "{\"name\":\"\",\"city\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.city").exists());
    }

    @Test
    void listIsPaginated() throws Exception {
        createVenue("Paged A");
        createVenue("Paged B");

        mockMvc.perform(get("/api/v1/venues").param("size", "1").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(2)));
    }

    @Test
    void pageSizeIsCappedAt100() throws Exception {
        mockMvc.perform(get("/api/v1/venues").param("size", "5000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void unknownSortPropertyIs400() throws Exception {
        mockMvc.perform(get("/api/v1/venues").param("sort", "password"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(startsWith("https://seatlock.dev/problems/")));
    }

    @Test
    void unknownVenueIs404() throws Exception {
        mockMvc.perform(get("/api/v1/venues/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Venue 999999 not found."));
        postJson("/api/v1/venues/999999/seats", seatsBody("Stalls", List.of("A"), 5))
                .andExpect(status().isNotFound());
    }

    @Test
    void addSeatsThenVenueShowsSectionsAndRows() throws Exception {
        long venueId = createVenue("Layout Hall");

        postJson("/api/v1/venues/" + venueId + "/seats", seatsBody("Stalls", List.of("A", "B"), 10))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.section").value("Stalls"))
                .andExpect(jsonPath("$.seatsCreated").value(20));
        postJson("/api/v1/venues/" + venueId + "/seats", seatsBody("Balcony", List.of("AA", "A"), 5))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/venues/" + venueId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSeats").value(30))
                .andExpect(jsonPath("$.sections[0].name").value("Balcony"))
                .andExpect(jsonPath("$.sections[0].rows[0].label").value("A"))
                .andExpect(jsonPath("$.sections[0].rows[1].label").value("AA"))
                .andExpect(jsonPath("$.sections[1].name").value("Stalls"))
                .andExpect(jsonPath("$.sections[1].seatCount").value(20));
    }

    @Test
    void duplicateRowInSectionIs409() throws Exception {
        long venueId = createVenue("Duplicate Hall");
        postJson("/api/v1/venues/" + venueId + "/seats", seatsBody("Stalls", List.of("A"), 10))
                .andExpect(status().isCreated());

        postJson("/api/v1/venues/" + venueId + "/seats", seatsBody("Stalls", List.of("B", "A"), 10))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://seatlock.dev/problems/conflict"));
    }

    @Test
    void invalidLayoutIs400() throws Exception {
        long venueId = createVenue("Invalid Hall");

        postJson("/api/v1/venues/" + venueId + "/seats", seatsBody("Stalls", List.of("a1"), 10))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['rowLabels[0]']").exists());
        postJson("/api/v1/venues/" + venueId + "/seats", seatsBody("Stalls", List.of("A"), 0))
                .andExpect(status().isBadRequest());
        postJson("/api/v1/venues/" + venueId + "/seats", seatsBody("Stalls", List.of(), 10))
                .andExpect(status().isBadRequest());
        postJson("/api/v1/venues/" + venueId + "/seats", seatsBody("Stalls", List.of("A", "A"), 10))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Row labels must be unique."));
    }

    @Test
    void largeLayoutIsInsertedInBatches() throws Exception {
        long venueId = createVenue("Stadium");
        List<String> rows = IntStream.range(0, 50)
                .mapToObj(i -> i < 26 ? String.valueOf((char) ('A' + i)) : "A" + (char) ('A' + i - 26))
                .toList();

        postJson("/api/v1/venues/" + venueId + "/seats", seatsBody("Lower Bowl", rows, 200))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seatsCreated").value(10_000));

        mockMvc.perform(get("/api/v1/venues/" + venueId))
                .andExpect(jsonPath("$.totalSeats").value(10_000))
                .andExpect(jsonPath("$.sections[0].rows.length()").value(50));
    }

    @Test
    void seatsAreListedPageByPage() throws Exception {
        long venueId = createVenue("Seat List Hall");
        postJson("/api/v1/venues/" + venueId + "/seats", seatsBody("Stalls", List.of("A", "B"), 3))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/venues/" + venueId + "/seats").param("size", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].row").value("A"))
                .andExpect(jsonPath("$.content[0].number").value(1))
                .andExpect(jsonPath("$.content[3].row").value("B"));
    }

    long createVenue(String name) throws Exception {
        String body = postJson("/api/v1/venues", "{\"name\":\"" + name + "\",\"city\":\"Hyderabad\"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(body);
        return node.get("id").asLong();
    }

    String seatsBody(String section, List<String> rows, int seatsPerRow) throws Exception {
        return json.writeValueAsString(Map.of(
                "section", section, "rowLabels", rows, "seatsPerRow", seatsPerRow));
    }

    ResultActions postJson(String path, String body) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
