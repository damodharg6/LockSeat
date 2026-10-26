package com.damodhar.seatlock.user;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates one ORGANISER account on startup, only when the "dev" profile is active. Outside dev
 * this bean does not exist, so there is no endpoint, flag or migration that can create an
 * organiser in production. Safe to run on every start: it does nothing if the account exists.
 */
@Component
@Profile("dev")
public class DevOrganiserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevOrganiserSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;
    private final String fullName;

    public DevOrganiserSeeder(UserRepository users, PasswordEncoder passwordEncoder,
                              @Value("${seatlock.dev.organiser.email}") String email,
                              @Value("${seatlock.dev.organiser.password}") String password,
                              @Value("${seatlock.dev.organiser.full-name}") String fullName) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.email = email.strip().toLowerCase(Locale.ROOT);
        this.password = password;
        this.fullName = fullName;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.existsByEmail(email)) {
            return;
        }
        users.save(new User(email, passwordEncoder.encode(password), fullName, Role.ORGANISER));
        log.info("Created dev organiser account {}", email);
    }
}
