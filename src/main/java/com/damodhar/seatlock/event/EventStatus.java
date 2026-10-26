package com.damodhar.seatlock.event;

import java.util.Set;

/**
 * Event lifecycle: DRAFT -> PUBLISHED -> CANCELLED, and DRAFT -> CANCELLED. CANCELLED is
 * terminal and nothing goes back to DRAFT.
 */
public enum EventStatus {
    DRAFT,
    PUBLISHED,
    CANCELLED;

    public boolean canTransitionTo(EventStatus target) {
        return switch (this) {
            case DRAFT -> Set.of(PUBLISHED, CANCELLED).contains(target);
            case PUBLISHED -> target == CANCELLED;
            case CANCELLED -> false;
        };
    }
}
