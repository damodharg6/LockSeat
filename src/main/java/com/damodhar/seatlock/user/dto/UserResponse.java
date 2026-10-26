package com.damodhar.seatlock.user.dto;

import java.time.Instant;

import com.damodhar.seatlock.user.Role;
import com.damodhar.seatlock.user.User;

/** Public view of a user. Deliberately has no password field. */
public record UserResponse(Long id, String email, String fullName, Role role, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole(),
                user.getCreatedAt());
    }
}
