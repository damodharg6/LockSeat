package com.damodhar.seatlock.user;

public enum Role {
    /** Buys tickets. Every self-registered account starts here. */
    BUYER,
    /** Creates venues, seats and events. */
    ORGANISER,
    /** Full access. */
    ADMIN
}
