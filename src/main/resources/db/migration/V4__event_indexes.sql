-- Indexes for the M3 read paths. Each one exists because a specific query needs it.

-- GET /api/v1/events: WHERE status = 'PUBLISHED' AND starts_at BETWEEN :from AND :to
-- ORDER BY starts_at. Equality column first, range/sort column second, so Postgres can walk
-- the index in order and stop after one page instead of sorting every published event.
CREATE INDEX idx_event_status_starts_at ON event (status, starts_at);

-- GET /api/v1/events/{id}/seats?status=AVAILABLE and the per-status seat counts. Without it,
-- filtering by status scans every seat of the event; M4 holds will also look seats up this way.
-- Lookups by event_id alone are already served by the unique (event_id, seat_id) index.
CREATE INDEX idx_event_seat_event_status ON event_seat (event_id, status);

-- Foreign keys Postgres does not index by itself, used to list an organiser's events and to
-- join events to venues.
CREATE INDEX idx_event_organiser ON event (organiser_id);
CREATE INDEX idx_event_venue ON event (venue_id);
