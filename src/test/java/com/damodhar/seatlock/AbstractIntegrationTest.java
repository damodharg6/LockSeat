package com.damodhar.seatlock;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

import com.damodhar.seatlock.auth.TokenService;
import com.damodhar.seatlock.user.Role;
import com.damodhar.seatlock.user.User;
import com.damodhar.seatlock.user.UserRepository;

/**
 * Base class for integration tests. One PostgreSQL container is started for the whole test run
 * and shared by every *IT class, so Flyway migrations run against the real database engine.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected TokenService tokenService;

    /** Creates a fresh user with the given role and returns an "Authorization" header value for it. */
    protected String bearer(Role role) {
        String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.seatlock.dev";
        User user = userRepository.save(new User(email, "not-a-real-hash", "Test " + role, role));
        return "Bearer " + tokenService.issue(user).accessToken();
    }
}
