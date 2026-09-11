# fantasy-db-service

The data persistence service for the Fantasy Hockey app. It owns the database and
exposes a REST API that the BFF (`fantasy-bff`) calls. The BFF never talks
to the database directly — all persistence goes through this service.

**Stack:** Java 25 · Spring Boot 4 · Spring Data JPA · PostgreSQL · Flyway · Gradle

## API (v1)

Base path: `/api/v1`. It covers users (sign-in identities, usernames, avatars, password reset
and email verification tokens), saved projections and their public shares, and premium access
(provider subscriptions and admin grants).

The endpoint reference is the OpenAPI spec, not this file: Swagger UI at `/swagger-ui.html`,
and the committed snapshot in `specs/openapi.yaml`, which `OpenApiSpecSnapshotTest` keeps in
step with the code. `CLAUDE.md` describes what each feature package does and why.

> Password **hashing is the BFF's responsibility** — this service stores and
> returns the hash verbatim. It never sees or handles raw passwords.

Email matching is **case-insensitive** (enforced by a `LOWER(email)` unique index).

## Running locally

Start the local Postgres defined in `docker-compose.yml`, then run the service with the `local`
profile:

```bash
docker compose up -d

# INTERNAL_API_KEY is required: without it the service refuses to start. Give the BFF the
# same value as its DB_INTERNAL_API_KEY (see DEPLOYMENT.md).
export INTERNAL_API_KEY=$(openssl rand -hex 32)

# DB_PASSWORD has no default; the local value is the one in docker-compose.yml.
SPRING_PROFILES_ACTIVE=local DB_PASSWORD=… ./gradlew bootRun
```

Run tests (use in-memory H2 — no database needed):

```bash
./gradlew test
```

## Configuration

| Env var | Default | Notes |
|---|---|---|
| `PORT` | `8086` | HTTP port |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `localhost` / `5432` / `fantasy` | Postgres connection |
| `DB_USER` | `fantasy` | Postgres user |
| `DB_PASSWORD` | none (required) | Postgres password |
| `INTERNAL_API_KEY` | none (required) | Shared key the BFF sends as `X-Internal-Api-Key`; blank refuses to start |
| `CURRENT_SEASON` | `20262027` | Season new projections are stamped with |
| `PASSWORD_RESET_TOKEN_TTL` | `PT30M` | How long a password reset token stays valid |
| `APP_VERSION` | `dev` | Version reported on `/actuator/info` |
| `SENTRY_DSN` / `SENTRY_ENVIRONMENT` / `SENTRY_RELEASE` | unset | Error reporting; inert without `SENTRY_DSN` |

Schema is managed by Flyway migrations in `src/main/resources/db/migration`.

## Security note

This service is deployed as an **internal-only** Coolify service (no public domain) and
its `/api/**` endpoints require a shared `X-Internal-Api-Key` header (set via
`INTERNAL_API_KEY`); without it requests get `401`. So its user endpoints — including
password hashes — are **not** internet-reachable: they sit behind both the missing public
route and the API key. This is a perimeter check between trusted services, not strong
per-user auth — see `DEPLOYMENT.md` for the rationale and hardening options.
