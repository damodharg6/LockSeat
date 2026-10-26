package com.damodhar.seatlock.common.error;

/** Thrown when a request conflicts with the current state, e.g. a duplicate. Mapped to 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
