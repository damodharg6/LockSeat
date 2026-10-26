package com.damodhar.seatlock.common.error;

/** Thrown when a requested resource does not exist. Mapped to 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
