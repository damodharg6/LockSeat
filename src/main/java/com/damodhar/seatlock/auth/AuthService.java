package com.damodhar.seatlock.auth;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.damodhar.seatlock.auth.dto.LoginRequest;
import com.damodhar.seatlock.auth.dto.SignupRequest;
import com.damodhar.seatlock.auth.dto.TokenResponse;
import com.damodhar.seatlock.common.error.BadRequestException;
import com.damodhar.seatlock.common.error.ConflictException;
import com.damodhar.seatlock.common.error.UnauthorizedException;
import com.damodhar.seatlock.user.Role;
import com.damodhar.seatlock.user.User;
import com.damodhar.seatlock.user.UserRepository;
import com.damodhar.seatlock.user.dto.UserResponse;

@Service
public class AuthService {

    static final String INVALID_CREDENTIALS = "Invalid email or password.";

    /** BCrypt only looks at the first 72 bytes; longer input would be silently truncated. */
    static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    /**
     * Hash checked when the email is unknown, so a login for a missing account costs the same
     * BCrypt time as a wrong password and response timing does not reveal which emails exist.
     */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.dummyHash = passwordEncoder.encode("timing-equaliser-password");
    }

    @Transactional
    public UserResponse signup(SignupRequest request) {
        String email = normalise(request.email());
        if (passwordBytes(request.password()) > MAX_PASSWORD_BYTES) {
            throw new BadRequestException("Password must be at most " + MAX_PASSWORD_BYTES + " bytes.");
        }
        if (users.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists.");
        }
        User user = users.save(new User(email, passwordEncoder.encode(request.password()),
                request.fullName().strip(), Role.BUYER));
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        Optional<User> user = users.findByEmail(normalise(request.email()));
        boolean passwordMatches = passwordBytes(request.password()) <= MAX_PASSWORD_BYTES
                && passwordEncoder.matches(request.password(), user.map(User::getPasswordHash).orElse(dummyHash));
        if (user.isEmpty() || !passwordMatches) {
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
        return tokenService.issue(user.get());
    }

    static String normalise(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private static int passwordBytes(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length;
    }
}
