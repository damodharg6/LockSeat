package com.damodhar.seatlock.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.damodhar.seatlock.auth.dto.TokenResponse;
import com.damodhar.seatlock.user.Role;
import com.damodhar.seatlock.user.User;

class TokenServiceTest {

    static final JwtProperties PROPERTIES =
            new JwtProperties("unit-test-secret-that-is-long-enough-1234", "seatlock", Duration.ofMinutes(15));

    final SecurityConfig config = new SecurityConfig();

    @Test
    void tokenCarriesUserIdEmailRoleAndFifteenMinuteExpiry() {
        var key = config.jwtSigningKey(PROPERTIES);
        Clock clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);
        TokenService tokens = new TokenService(config.jwtEncoder(key), PROPERTIES, clock);
        JwtDecoder decoder = config.jwtDecoder(key, PROPERTIES);
        User user = new User("asha@example.com", "hash", "Asha", Role.ORGANISER);
        ReflectionTestUtils.setField(user, "id", 42L);

        TokenResponse response = tokens.issue(user);
        Jwt jwt = decoder.decode(response.accessToken());

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900);
        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsString("email")).isEqualTo("asha@example.com");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("ORGANISER");
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void missingSecretFailsStartup() {
        assertThatThrownBy(() -> config.jwtSigningKey(new JwtProperties("", "seatlock", Duration.ofMinutes(15))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET is not set");
    }

    @Test
    void shortSecretFailsStartup() {
        assertThatThrownBy(() -> config.jwtSigningKey(new JwtProperties("too-short", "seatlock", Duration.ofMinutes(15))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }
}
