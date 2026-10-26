package com.damodhar.seatlock.common.error;

/** Thrown when the caller is signed in but may not act on this resource. Mapped to 403. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
