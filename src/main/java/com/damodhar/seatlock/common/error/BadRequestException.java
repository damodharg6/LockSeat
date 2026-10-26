package com.damodhar.seatlock.common.error;

/** Thrown when a request is well-formed but breaks a business rule about its content. Mapped to 400. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
