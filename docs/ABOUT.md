# About SeatLock

SeatLock is a portfolio project: an event ticketing backend that focuses on one hard problem,
selling reserved seats correctly when many customers want the same seat at the same moment.

## Current state (M1)

The project foundation is in place: a Spring Boot 3.5 / Java 21 API with consistent RFC 9457
error responses, a paginated response envelope, Flyway-managed PostgreSQL schema, health and
OpenAPI endpoints, integration tests on real PostgreSQL via Testcontainers, CI on GitHub Actions,
and a React + TypeScript web app that shows API status and build progress.

Venues can be created with sections and seats. Seats are added in rectangular blocks (rows ×
seats per row) and inserted in batches using a pooled database sequence, so a 10,000-seat layout
is one request. Every seat position is unique per section, enforced by a database constraint.
The web app lists venues and draws each venue's seat map.

There is no sign-in yet, and no events, holds or sales.

## Planned

The full plan, including how double selling is prevented with atomic PostgreSQL updates and a
unique ticket constraint, is in [DESIGN.md](DESIGN.md). Milestones are in
[MILESTONES.md](MILESTONES.md).
