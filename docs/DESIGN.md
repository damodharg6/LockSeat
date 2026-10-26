# SeatLock design

This is the target design. docs/MILESTONES.md says which parts are built.

## 1. Goal

SeatLock is an event ticketing backend. Organisers publish events at venues with reserved seating;
customers hold seats, pay, and receive tickets.

The one promise everything else serves: **a seat is never sold twice, even when thousands of
customers click the same seat at the same moment, and even when several app instances run at once.**

Non-goals for now: general admission (unseated) events, refunds, resale, real payment providers,
multi-currency. Money is INR only.

## 2. Architecture

```
 React web app (Netlify)
        |
        v  HTTPS / JSON
 Spring Boot API  x N instances (stateless, Render)
   |            |              |
   v            v              v
 PostgreSQL   Redis          Kafka
 (source of   (cache, rate   (domain events from
  truth)       limits)        the outbox)
```

- **PostgreSQL** is the only source of truth for seat state, orders and tickets. Every rule that
  protects the core promise is enforced inside a PostgreSQL transaction or constraint.
- **Redis** is an optimisation only: seat-map caching and rate limiting. If Redis is down or loses
  data, the system gets slower, never wrong.
- **Kafka** carries domain events (order confirmed, hold expired) to downstream consumers.
  Events are written to an outbox table in the same transaction as the business change, then
  published, so a database commit and its event can never disagree.
- **App instances hold no state.** Any instance can serve any request; scaling out is just adding
  instances.

## 3. Domain model

| Entity | Purpose | Notes |
|---|---|---|
| `Venue` | A physical place | name, city |
| `Section` | Part of a venue | e.g. "Balcony" |
| `Seat` | A physical seat | section, row label, seat number; unique per section |
| `Event` | A show at a venue | DRAFT → PUBLISHED → CANCELLED; starts_at, sales window |
| `PriceTier` | Price for a group of seats | `price_paise BIGINT` |
| `EventSeat` | One seat for one event | the row that gets locked; status, version, hold reference |
| `Hold` | A customer's temporary claim on seats | expires_at; ACTIVE → CONFIRMED / EXPIRED / RELEASED |
| `Order` | Checkout of a hold | total_paise, status, idempotency key |
| `Ticket` | Proof of purchase for one event seat | **unique on event_seat_id** |
| `User` | Buyer or organiser | role BUYER / ORGANISER / ADMIN |
| `OutboxEvent` | Pending domain event | published flag, created_at |

`EventSeat` is the bulk table: publishing an event with a 40,000-seat stadium inserts 40,000 rows.
It uses a pooled sequence (allocationSize 50) and JDBC batching so that insert is a few hundred
round trips, not 40,000.

### Seat state machine (per EventSeat)

```
AVAILABLE --hold--> HELD --confirm--> SOLD
    ^                 |
    +---expire/release+
```

`SOLD` is terminal. There is no path from SOLD back to AVAILABLE in this version.

## 4. How a seat is never sold twice

Three layers, each enough on its own for the case it covers.

### Layer 1: atomic conditional hold

Holding seats is one SQL statement inside a transaction:

```sql
UPDATE event_seat
   SET status = 'HELD', hold_id = :holdId, held_until = :expiresAt, version = version + 1
 WHERE event_id = :eventId
   AND id = ANY(:seatIds)
   AND (status = 'AVAILABLE' OR (status = 'HELD' AND held_until < now()));
```

The service compares the updated row count with the number of seats requested. If they differ,
some seat was taken: the transaction rolls back and the client gets `409 Conflict` listing the
unavailable seats. A hold is all-or-nothing.

Why this works: PostgreSQL takes a row lock on each row it updates and re-checks the `WHERE`
clause after waiting for a competing transaction (READ COMMITTED re-evaluation). Of two
concurrent requests for the same seat, exactly one sees `AVAILABLE`; the other matches zero rows.
No application lock, no distributed lock, no retry loop.

To avoid deadlocks when two customers request overlapping seat sets in different orders, the
seat ids are sorted before the statement, and rows are locked in id order.

### Layer 2: confirm only what you hold

Checkout moves seats from HELD to SOLD with the same pattern:

```sql
UPDATE event_seat SET status = 'SOLD', version = version + 1
 WHERE hold_id = :holdId AND status = 'HELD' AND held_until >= now();
```

If the hold expired a millisecond ago and someone else re-held a seat, the row count is short and
the order fails cleanly instead of selling a seat that now belongs to someone else.

### Layer 3: database constraint as the last line

`ticket.event_seat_id` has a `UNIQUE` constraint. Even a bug in layers 1–2 cannot create two
tickets for one seat; the second insert fails and the transaction rolls back.

### Why not Redis locks (or `synchronized`)

- `synchronized` / in-memory locks only work inside one JVM. With two instances they protect nothing.
- A Redis lock (SETNX / Redlock) is a second source of truth. A failover, eviction or long GC pause
  can let two holders believe they own the lock while PostgreSQL has no idea. Keeping the decision
  in the same database that stores the result removes that whole class of bugs.

### Hold expiry

Holds last 10 minutes (configurable via `SEATLOCK_HOLD_TTL`). Expiry is **lazy for correctness**:
the conditional `UPDATE` above treats an expired HELD seat as available, so nothing waits on a
background job. A scheduled sweeper also marks expired holds EXPIRED and frees their seats, using
`FOR UPDATE SKIP LOCKED` so several instances can run it at once without stepping on each other.

### Proof

An integration test (Testcontainers PostgreSQL) fires N concurrent hold requests for the same seats
from a thread pool and asserts exactly one succeeds and the rest get 409. A later milestone adds a
k6 load test against the running app with several instances.

## 5. Checkout and payments

- Payment goes through a `PaymentGateway` interface. The only implementation is a fake gateway
  that approves or declines based on test card numbers. No real money, no card data stored.
- `POST /api/orders` requires an `Idempotency-Key` header. The key is stored with a unique
  constraint per user; a retried request returns the original order instead of charging twice.
- Order confirmation, seat SOLD update, ticket inserts and the outbox row are one transaction.

## 6. API conventions

- REST + JSON under `/api/v1`.
- Controllers only map HTTP to service calls. Request and response bodies are Java `record` DTOs;
  entities never leave the service layer.
- Errors: `common/error/GlobalExceptionHandler` returns RFC 9457 `ProblemDetail`
  (`application/problem+json`) with a stable `type` URI per error, e.g. `.../seat-unavailable`.
- Lists: `?page=&size=&sort=`, response is `common/web/PageResponse<T>`
  (`content, page, size, totalElements, totalPages`). Max size 100.
- Money in responses is paise as an integer plus a formatted display string.
- Times are ISO-8601 UTC (`Instant`); the web app formats to local time.

## 7. Security

- **Stateless JWT.** `POST /api/v1/auth/login` returns an HS256 access token signed with
  `JWT_SECRET` (at least 32 bytes). Claims: `sub` = user id, `email`, `role`, `iss`, `iat`, `exp`.
  Tokens last 15 minutes. Every instance validates tokens with the shared secret, so there is no
  session store and any instance can serve any request.
- Validation uses Spring Security's OAuth2 resource server (`NimbusJwtDecoder`): signature,
  algorithm, expiry and issuer are checked; the `role` claim becomes `ROLE_<role>`.
- Passwords are hashed with BCrypt (cost 10, random salt per hash). Passwords longer than 72 bytes
  are rejected, because BCrypt ignores everything after byte 72.
- Login returns the same 401 for unknown email and wrong password, and runs a BCrypt check against
  a dummy hash for unknown emails so response time does not reveal which emails exist.
- Roles: BUYER (default on signup) buys; ORGANISER creates venues, seats and events; ADMIN
  everything. Access rules are URL-based and live in one place, `auth/SecurityConfig`.
- Sessions are STATELESS and CSRF protection is off: the API reads tokens only from the
  `Authorization` header, never from cookies, so a cross-site request cannot carry credentials.
- 401 and 403 are written as RFC 9457 ProblemDetail by `ProblemDetailAuthHandler`, matching every
  other error.
- CORS allows only the configured web origin (`SEATLOCK_WEB_ORIGIN`).
- There is no public way to become an organiser. Locally, the `dev` profile seeds one organiser
  account on startup; that bean does not exist in any other profile.
- Planned: rate limiting on login and hold endpoints, counters in Redis (M6).

## 8. Data and migrations

- Flyway owns the schema; Hibernate `ddl-auto: validate` only checks entities match it.
- Applied migrations are never edited; every change is a new `V<n>__description.sql`.
- Money columns are `BIGINT` paise. Ids are `BIGINT` from sequences.
- Key indexes: `event_seat (event_id, status)`, `event_seat (hold_id)`, `hold (expires_at)
  WHERE status = 'ACTIVE'`, unique `ticket (event_seat_id)`, unique `orders (user_id, idempotency_key)`.

## 9. Code layout

```
src/main/java/com/damodhar/seatlock/
  SeatLockApplication.java
  common/error/      GlobalExceptionHandler, domain exceptions
  common/web/        PageResponse
  venue/             VenueController, VenueService, VenueRepository, Venue, dto/
  event/             ...
  inventory/         EventSeat, seat map
  hold/              ...
  order/             Order, Ticket, PaymentGateway
  user/              User, Role, UserController (/users/me), dev organiser seeder
  auth/              AuthController, AuthService, TokenService, SecurityConfig
  outbox/            OutboxEvent, publisher
src/main/resources/db/migration/   V1__..., V2__...
web/                               React + TypeScript + Vite
```

## 10. Testing

- `*Test.java`: unit tests with Mockito for service rules.
- `*IT.java`: integration tests extending `AbstractIntegrationTest`, which starts PostgreSQL
  (and Redis / Kafka when needed) with Testcontainers, shared across tests.
- Every business rule and error response has a test. `mvn verify` runs both and gates every commit
  and CI run.

## 11. Configuration and deployment

- All settings come from environment variables with local defaults in `application.yml`
  (`SPRING_DATASOURCE_URL`, `SEATLOCK_REDIS_URL`, `SEATLOCK_KAFKA_BOOTSTRAP`, `JWT_SECRET`,
  `SEATLOCK_HOLD_TTL`, `SEATLOCK_WEB_ORIGIN`). No secrets in the repo.
- `docker-compose.yml` runs PostgreSQL 16, Redis and Kafka for local development.
- API: Docker image deployed to Render from `render.yaml`. Web: Netlify site `seatlock-tickets`,
  base directory `web`.
- CI: GitHub Actions runs `mvn verify` and `npm ci && npm run lint && npm run build` on every PR.

## 12. Open questions for review

1. **Kafka in the hosted demo.** Render has no managed Kafka. Options: point to a free managed
   Kafka via env vars, or run Kafka only locally/CI and disable the outbox publisher in the hosted
   demo (`SEATLOCK_OUTBOX_ENABLED=false`). Draft assumes the second.
2. **Hold limits.** Draft: max 10 seats per hold, one active hold per customer per event.
3. **Hold TTL.** Draft: 10 minutes.
4. **Auth timing.** Resolved: auth moved to M2, before events, so every event and hold belongs to a
   real user from the start.
