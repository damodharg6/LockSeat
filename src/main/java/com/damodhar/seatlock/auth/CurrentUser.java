package com.damodhar.seatlock.auth;

import org.springframework.security.oauth2.jwt.Jwt;

import com.damodhar.seatlock.user.Role;

/** The caller as described by a validated access token. */
public record CurrentUser(Long id, Role role) {

    public static CurrentUser from(Jwt jwt) {
        return jwt == null ? null
                : new CurrentUser(Long.valueOf(jwt.getSubject()), Role.valueOf(jwt.getClaimAsString(TokenService.CLAIM_ROLE)));
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
