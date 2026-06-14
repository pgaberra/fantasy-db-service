# fantasy-db-service

The data persistence service for the Fantasy Hockey app. It owns the database and
exposes a small REST API that the BFF (`fantasy-bff`) calls. The BFF never talks
to the database directly — all persistence goes through this service.

**Stack:** Java 25 · Spring Boot 4 · Spring Data JPA · PostgreSQL · Flyway · Gradle

## API (v1)

Base path: `/api/v1`

| Method | Path | Purpose | Responses |
|---|---|---|---|
| `GET` | `/users?email={email}` | Look up a user by email | `200` UserResponse · `404` if missing |
| `GET` | `/users/exists?email={email}` | Check if an email is taken | `200` `{ "exists": true|false }` |
| `POST` | `/users` | Create a user | `201` UserResponse · `400` invalid · `409` duplicate |

**UserResponse**
```json
{ "id": "uuid", "email": "user@example.com", "passwordHash": "..." }
```

**CreateUserRequest**
```json
{ "email": "user@example.com", "passwordHash": "..." }
```

> Password **hashing is the BFF's responsibility** — this service stores and
> returns the hash verbatim. It never sees or handles raw passwords.

Email matching is **case-insensitive** (enforced by a `LOWER(email)` unique index).

Swagger UI: `/swagger-ui.html`

## Running locally

Requires a local PostgreSQL (or override `DB_*` env vars):

```bash
# defaults: localhost:5432, db "fantasy", user/password "fantasy"
./gradlew bootRun
```

Run tests (use in-memory H2 — no database needed):

```bash
./gradlew test
```

## Configuration

| Env var | Default | Notes |
|---|---|---|
| `PORT` | `8086` | HTTP port |
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | local defaults | Postgres connection |

Schema is managed by Flyway migrations in `src/main/resources/db/migration`.

## Security note

This service is deployed as an **internal-only** Coolify service (no public domain) and
its `/api/**` endpoints require a shared `X-Internal-Api-Key` header (set via
`INTERNAL_API_KEY`); without it requests get `401`. So its user endpoints — including
password hashes — are **not** internet-reachable: they sit behind both the missing public
route and the API key. This is a perimeter check between trusted services, not strong
per-user auth — see `DEPLOYMENT.md` for the rationale and hardening options.
