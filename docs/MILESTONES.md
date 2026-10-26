# SeatLock milestones

Status: draft for review. One branch and one pull request per milestone, named `mN-short-name`.
Each milestone is done when `mvn verify` and `npm ci && npm run lint && npm run build` pass, the
web app shows the new API features, and README.md, docs/ABOUT.md and the Build progress list in
`web/src/pages/Home.tsx` are updated.

| # | Branch | Title | Status |
|---|---|---|---|
| M0 | `m0-foundation` | Project foundation | Done |
| M1 | `m1-venues` | Venues, sections and seats | Not started |
| M2 | `m2-events-inventory` | Events, price tiers and seat inventory | Not started |
| M3 | `m3-auth` | Users and JWT auth | Not started |
| M4 | `m4-holds` | Seat holds under concurrency | Not started |
| M5 | `m5-checkout` | Checkout, fake payments and tickets | Not started |
| M6 | `m6-redis` | Seat-map cache and rate limiting | Not started |
| M7 | `m7-outbox-kafka` | Outbox and Kafka events | Not started |
| M8 | `m8-load-ops` | Load test, observability, hardening | Not started |

---

## M0 · Project foundation · `m0-foundation`

- Maven project, Java 21, Spring Boot 3.5, package `com.damodhar.seatlock`.
- `application.yml` reading env vars with local defaults; `ddl-auto: validate`; Flyway enabled.
- `common/error/GlobalExceptionHandler` returning RFC 9457 ProblemDetail (validation, not found,
  generic 500 without leaking internals).
- `common/web/PageResponse<T>`.
- `AbstractIntegrationTest` with a shared PostgreSQL 16 Testcontainer.
- `docker-compose.yml` (PostgreSQL; Redis and Kafka join in M6 and M7), `Dockerfile`, `render.yaml`,
  `web/netlify.toml`.
- Actuator health and OpenAPI / Swagger UI.
- GitHub Actions: `mvn verify` and the web build on every PR.
- Web: Vite + React + TypeScript scaffold, brand tokens in `web/src/index.css`, Home page with the
  Build progress list.
- README.md and docs/ABOUT.md (what exists, how to run locally).

Tests: exception handler returns ProblemDetail shapes; app context starts against Testcontainers.

## M1 · Venues, sections and seats · `m1-venues`

- Migration `V1__venues_sections_seats.sql`.
- Organiser endpoints: create/list/get venues; add sections; bulk-create seats per section from a
  row/seat layout (e.g. rows A–T, seats 1–30).
- Seats unique per (section, row, number). `Seat` uses a pooled sequence (allocationSize 50) with
  JDBC batching.
- Paginated venue list.
- Web: venue list, venue detail with a simple seat grid.

Tests: duplicate seat rejected (409), unknown venue (404), invalid layout (400), batch insert of a
large layout.

## M2 · Events, price tiers and seat inventory · `m2-events-inventory`

- Migration `V2__events_inventory.sql`: `event`, `price_tier`, `event_seat`.
- Create event (DRAFT) at a venue, attach price tiers (`price_paise BIGINT`) to sections.
- Publish event: generates one `event_seat` per venue seat in batches (pooled sequence), status
  AVAILABLE. Publishing twice is rejected.
- Public endpoints: paginated list of published events; seat map for an event with status and price.
- Web: events list, event page with the seat map coloured by status and tier.

Tests: publish without price tiers fails; double publish fails; seat count matches venue; money
stays in paise end to end.

## M3 · Users and JWT auth · `m3-auth`

- Migration `V3__users.sql`.
- Register, login, `GET /api/me`. BCrypt passwords, JWT signed with `SEATLOCK_JWT_SECRET`.
- Roles CUSTOMER / ORGANISER / ADMIN; organisers manage only their own venues and events.
- CORS limited to `SEATLOCK_WEB_ORIGIN`.
- Web: sign up, sign in, sign out; organiser screens behind the organiser role.

Tests: wrong password (401), missing token (401), wrong role (403), organiser cannot edit another
organiser's event (403), duplicate email (409).

## M4 · Seat holds under concurrency · `m4-holds`

The core promise. See DESIGN.md §4.

- Migration `V4__holds.sql`: `hold`, plus `hold_id`, `held_until`, `version` on `event_seat`.
- `POST /api/events/{id}/holds` with seat ids: single conditional `UPDATE`, all-or-nothing,
  sorted ids to avoid deadlocks, 409 ProblemDetail listing unavailable seats.
- `DELETE /api/holds/{id}` releases a hold. `GET /api/holds/{id}` shows seats and expiry.
- Limits: max seats per hold, one active hold per customer per event, sales window checks.
- Scheduled sweeper expiring holds with `FOR UPDATE SKIP LOCKED`.
- Web: select seats on the map, hold them, countdown timer, release.

Tests: **N threads holding the same seat → exactly one wins**; overlapping seat sets never both
succeed and never deadlock; expired hold's seats can be re-held; releasing someone else's hold
fails (403); limits enforced.

## M5 · Checkout, fake payments and tickets · `m5-checkout`

- Migration `V5__orders_tickets.sql`: `orders` (unique `user_id, idempotency_key`), `ticket`
  (unique `event_seat_id`).
- `PaymentGateway` interface with a fake implementation driven by test card numbers.
- `POST /api/orders` with `Idempotency-Key`: confirm HELD → SOLD only for the caller's unexpired
  hold, create tickets, all in one transaction.
- `GET /api/orders` (paginated) and ticket details for the customer.
- Web: checkout page, order confirmation, "My tickets".

Tests: retried request with same key returns the same order and charges once; expired hold fails
checkout; declined payment sells nothing and leaves the hold active until it expires; ticket uniqueness
constraint rejects a second ticket for a seat.

## M6 · Seat-map cache and rate limiting · `m6-redis`

- Redis connection from `SEATLOCK_REDIS_URL`.
- Cache the seat map per event with short TTL, evicted on hold/confirm/expire.
- Rate limits on login and hold endpoints (429 ProblemDetail with `Retry-After`).
- App keeps working (uncached, unlimited) if Redis is unavailable.

Tests: cache evicted after a hold; rate limit returns 429; Redis down does not break holds.

## M7 · Outbox and Kafka events · `m7-outbox-kafka`

- Migration `V6__outbox.sql`.
- `OrderConfirmed`, `HoldExpired` written to the outbox in the business transaction.
- Publisher polls with `SKIP LOCKED`, sends to Kafka, marks published; at-least-once delivery.
- Example consumer: ticket confirmation notifier (logs / stores a notification record),
  idempotent on event id.
- `SEATLOCK_OUTBOX_ENABLED` flag for environments without Kafka.
- Web: organiser sales activity feed for an event.

Tests: rolled-back order writes no outbox row; publisher retries after Kafka failure; consumer
ignores duplicates.

## M8 · Load test, observability, hardening · `m8-load-ops`

- Actuator health/readiness, Micrometer metrics (holds won/lost, hold latency, expiries).
- k6 script: thousands of virtual users racing for the same seats against 2+ instances; result
  check shows zero double sales.
- Structured JSON logs with request ids; graceful shutdown.
- README section with load test results.
- Web: organiser dashboard with live seats sold / held / available.

Tests: health endpoints; metrics recorded on hold success and conflict.
