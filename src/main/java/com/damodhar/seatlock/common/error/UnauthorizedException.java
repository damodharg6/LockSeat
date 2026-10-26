package com.damodhar.seatlock.common.error;

/** Thrown when credentials are missing or wrong. Mapped to 401. */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
