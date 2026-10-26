package com.damodhar.seatlock.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class DevOrganiserSeederTest {

    @Mock
    UserRepository users;

    DevOrganiserSeeder seeder() {
        return new DevOrganiserSeeder(users, new BCryptPasswordEncoder(4), "Organiser@SeatLock.local",
                "organiser-dev-pass", "Dev Organiser");
    }

    @Test
    void createsOrganiserWhenMissing() {
        when(users.existsByEmail("organiser@seatlock.local")).thenReturn(false);

        seeder().run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ORGANISER);
        assertThat(saved.getValue().getEmail()).isEqualTo("organiser@seatlock.local");
        assertThat(saved.getValue().getPasswordHash()).startsWith("$2a$");
    }

    @Test
    void doesNothingWhenOrganiserExists() {
        when(users.existsByEmail("organiser@seatlock.local")).thenReturn(true);

        seeder().run(null);

        verify(users, never()).save(any());
    }

    @Test
    void onlyLoadedInDevProfile() {
        assertThat(DevOrganiserSeeder.class.getAnnotation(Profile.class).value()).containsExactly("dev");
    }
}
