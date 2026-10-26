package com.damodhar.seatlock.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bound from {@code seatlock.jwt.*}; the secret comes from the JWT_SECRET environment variable. */
@ConfigurationProperties("seatlock.jwt")
public record JwtProperties(String secret, String issuer, Duration ttl) {
}
