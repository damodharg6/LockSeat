# SeatLock

Event ticketing backend whose one promise is that **a seat is never sold twice**, even under
heavy concurrency and with several app instances running.

> Status: M0 (foundation) and M1 (venues and seats) are done. See
> [docs/MILESTONES.md](docs/MILESTONES.md) for what comes next and [docs/DESIGN.md](docs/DESIGN.md)
> for how it will work.

## What exists today

### API

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/venues` | Create a venue |
| `GET` | `/api/v1/venues` | List venues (paginated, `?page=&size=&sort=`) |
| `GET` | `/api/v1/venues/{id}` | Venue with its sections and rows |
| `POST` | `/api/v1/venues/{id}/seats` | Add a block of seats: `{section, rowLabels, seatsPerRow}` |
| `GET` | `/api/v1/venues/{id}/seats` | List seats (paginated) |

### Platform

- Spring Boot 3.5 API on Java 21, with Flyway, Hibernate in `validate` mode and PostgreSQL 16.
- Errors returned as RFC 9457 `ProblemDetail` JSON (`application/problem+json`).
- `PageResponse` envelope ready for paginated list endpoints.
- Health check at `/actuator/health`, OpenAPI at `/v3/api-docs`, Swagger UI at `/swagger-ui.html`.
- React + TypeScript web app in `web/`: API status, build progress, venue list, venue seat map and
  forms to add venues and seats.
- Unit tests (Mockito/MockMvc) and integration tests against a real PostgreSQL in Testcontainers.
- GitHub Actions CI, Dockerfile, Render blueprint and Netlify config.

## Run locally

Requirements: Java 21, Maven, Docker, Node 20+.

```bash
docker compose up -d          # PostgreSQL on localhost:5440
mvn spring-boot:run           # API on http://localhost:8080
cd web && npm ci && npm run dev   # web app on http://localhost:5173
```

## Test

```bash
mvn verify                                   # unit + integration tests (needs Docker)
cd web && npm ci && npm run lint && npm run build
```

## Configuration

All settings come from environment variables; `application.yml` holds local defaults only.

| Variable | Default | Purpose |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5440/seatlock` | Database URL |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | `seatlock` / `seatlock` | Database credentials |
| `PORT` | `8080` | HTTP port |
| `SEATLOCK_WEB_ORIGIN` | `http://localhost:5173` | Allowed CORS origin(s), comma separated |
| `VITE_API_URL` (web) | `http://localhost:8080` | API base URL for the web app |

## Deployment

- API: Docker image on Render, defined in `render.yaml`. Database credentials are set in the
  Render dashboard.
- Web: Netlify site `seatlock-tickets` with base directory `web`; `VITE_API_URL` is set in the
  Netlify dashboard.
