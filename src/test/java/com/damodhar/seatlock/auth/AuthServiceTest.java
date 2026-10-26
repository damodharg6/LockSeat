package com.damodhar.seatlock.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.damodhar.seatlock.auth.dto.LoginRequest;
import com.damodhar.seatlock.auth.dto.SignupRequest;
import com.damodhar.seatlock.auth.dto.TokenResponse;
import com.damodhar.seatlock.common.error.BadRequestException;
import com.damodhar.seatlock.common.error.ConflictException;
import com.damodhar.seatlock.common.error.UnauthorizedException;
import com.damodhar.seatlock.user.Role;
import com.damodhar.seatlock.user.User;
import com.damodhar.seatlock.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    /** Real BCrypt with the lowest cost so hashing is actually exercised but tests stay fast. */
    final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    @Mock
    UserRepository users;
    @Mock
    TokenService tokenService;

    AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, passwordEncoder, tokenService);
    }

    @Test
    void signupStoresLowercaseEmailBcryptHashAndBuyerRole() {
        when(users.existsByEmail("asha@example.com")).thenReturn(false);
        when(users.save(any(User.class))).thenAnswer(inv -> withId(inv.getArgument(0), 1L));

        var response = service.signup(new SignupRequest("  Asha@Example.COM ", "correct-horse", " Asha Rao "));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        User user = saved.getValue();
        assertThat(user.getEmail()).isEqualTo("asha@example.com");
        assertThat(user.getFullName()).isEqualTo("Asha Rao");
        assertThat(user.getRole()).isEqualTo(Role.BUYER);
        assertThat(user.getPasswordHash()).isNotEqualTo("correct-horse").startsWith("$2a$");
        assertThat(passwordEncoder.matches("correct-horse", user.getPasswordHash())).isTrue();
        assertThat(response.role()).isEqualTo(Role.BUYER);
    }

    @Test
    void samePasswordHashesDifferentlyEachTime() {
        when(users.save(any(User.class))).thenAnswer(inv -> withId(inv.getArgument(0), 1L));

        service.signup(new SignupRequest("a@example.com", "same-password", "A"));
        service.signup(new SignupRequest("b@example.com", "same-password", "B"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(0).getPasswordHash())
                .isNotEqualTo(saved.getAllValues().get(1).getPasswordHash());
    }

    @Test
    void signupWithTakenEmailIsConflictRegardlessOfCase() {
        when(users.existsByEmail("asha@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.signup(new SignupRequest("ASHA@example.com", "correct-horse", "Asha")))
                .isInstanceOf(ConflictException.class);
        verify(users, never()).save(any());
    }

    @Test
    void passwordLongerThan72BytesIsRejected() {
        // 30 characters but 90 bytes in UTF-8: BCrypt would silently ignore everything after byte 72.
        String password = "पासवर्ड".repeat(4) + "पा";

        assertThatThrownBy(() -> service.signup(new SignupRequest("a@example.com", password, "A")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("72 bytes");
    }

    @Test
    void loginWithCorrectPasswordIssuesToken() {
        User user = withId(new User("asha@example.com", passwordEncoder.encode("correct-horse"), "Asha", Role.BUYER), 7L);
        when(users.findByEmail("asha@example.com")).thenReturn(Optional.of(user));
        TokenResponse token = new TokenResponse("jwt", "Bearer", 900);
        when(tokenService.issue(user)).thenReturn(token);

        assertThat(service.login(new LoginRequest("Asha@Example.com", "correct-horse"))).isSameAs(token);
    }

    @Test
    void wrongPasswordAndUnknownEmailFailWithTheSameMessage() {
        User user = withId(new User("asha@example.com", passwordEncoder.encode("correct-horse"), "Asha", Role.BUYER), 7L);
        when(users.findByEmail("asha@example.com")).thenReturn(Optional.of(user));
        when(users.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("asha@example.com", "wrong-password")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage(AuthService.INVALID_CREDENTIALS);
        assertThatThrownBy(() -> service.login(new LoginRequest("nobody@example.com", "wrong-password")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage(AuthService.INVALID_CREDENTIALS);
        verify(tokenService, never()).issue(any());
    }

    private static User withId(User user, Long id) {
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
