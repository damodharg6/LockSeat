package com.damodhar.seatlock.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void notFoundBecomesProblemDetail() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(GlobalExceptionHandler.TYPE_BASE + "not-found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Venue 7 not found"));
    }

    @Test
    void conflictBecomesProblemDetail() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(GlobalExceptionHandler.TYPE_BASE + "conflict"));
    }

    @Test
    void validationErrorsListEachField() throws Exception {
        mockMvc.perform(post("/test/validate").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(GlobalExceptionHandler.TYPE_BASE + "validation-error"))
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        mockMvc.perform(post("/test/validate").contentType(MediaType.APPLICATION_JSON).content("{nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void unexpectedErrorHidesInternals() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Something went wrong. Please try again later."));
    }

    record Body(@NotBlank String name) {
    }

    @RestController
    static class TestController {

        @GetMapping("/test/not-found")
        void notFound() {
            throw new NotFoundException("Venue 7 not found");
        }

        @GetMapping("/test/conflict")
        void conflict() {
            throw new ConflictException("Already exists");
        }

        @GetMapping("/test/boom")
        void boom() {
            throw new IllegalStateException("secret internal detail");
        }

        @PostMapping("/test/validate")
        void validate(@Valid @RequestBody Body body) {
        }
    }
}
