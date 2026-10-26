# About SeatLock

SeatLock is a portfolio project: an event ticketing backend that focuses on one hard problem,
selling reserved seats correctly when many customers want the same seat at the same moment.

## Current state (M0)

The project foundation is in place: a Spring Boot 3.5 / Java 21 API with consistent RFC 9457
error responses, a paginated response envelope, Flyway-managed PostgreSQL schema, health and
OpenAPI endpoints, integration tests on real PostgreSQL via Testcontainers, CI on GitHub Actions,
and a React + TypeScript web app that shows API status and build progress.

No ticketing features exist yet. Venues and seats arrive in M1.

## Planned

The full plan, including how double selling is prevented with atomic PostgreSQL updates and a
unique ticket constraint, is in [DESIGN.md](DESIGN.md). Milestones are in
[MILESTONES.md](MILESTONES.md).
