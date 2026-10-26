# About SeatLock

SeatLock is a portfolio project: an event ticketing backend that focuses on one hard problem,
selling reserved seats correctly when many customers want the same seat at the same moment.

## Current state (M2 in review)

The project foundation is in place: a Spring Boot 3.5 / Java 21 API with consistent RFC 9457
error responses, a paginated response envelope, Flyway-managed PostgreSQL schema, health and
OpenAPI endpoints, integration tests on real PostgreSQL via Testcontainers, CI on GitHub Actions,
and a React + TypeScript web app that shows API status and build progress.

Venues can be created with sections and seats. Seats are added in rectangular blocks (rows ×
seats per row) and inserted in batches using a pooled database sequence, so a 10,000-seat layout
is one request. Every seat position is unique per section, enforced by a database constraint.
The web app lists venues and draws each venue's seat map.

People can sign up and sign in. Sign-up always creates a buyer account; passwords are hashed with
BCrypt, and sign-in returns a 15-minute JWT access token. The API keeps no sessions, so any
instance can check any token. Anyone can browse venues and seats, but only organisers and admins
can create them. For local development, an organiser account is created automatically.

There are no events, holds or sales yet.

## Planned

The full plan, including how double selling is prevented with atomic PostgreSQL updates and a
unique ticket constraint, is in [DESIGN.md](DESIGN.md). Milestones are in
[MILESTONES.md](MILESTONES.md).
