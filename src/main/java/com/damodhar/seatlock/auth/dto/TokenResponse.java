package com.damodhar.seatlock.auth.dto;

/** {@code expiresIn} is in seconds, as in OAuth 2.0 token responses. */
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}
