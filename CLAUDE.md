# CLAUDE.md — fantasy-db-service

Persistence microservice for the fantasy hockey tool. Owns the database and
exposes a small REST API for the BFF (`fantasy-bff`) to manage data. v1 scope:
**users only** (create user, look up by email, existence check). Projections and
other entities will come later.

> ⚠️ This service is currently **unauthenticated** and returns password hashes.
> It must not be publicly exposed without auth. Adding an API key / network
> restriction between the BFF and this service is a known follow-up.

## Tech stack

- Java 25, Spring Boot 4.0.5, Gradle (wrapper: `./gradlew`)
- Spring WebMVC, Spring Data JPA, Bean Validation, Actuator
- PostgreSQL (runtime), Flyway migrations
- Tests: JUnit 5, H2 in-memory (PostgreSQL mode)
- springdoc OpenAPI / Swagger UI

## Common commands

```bash
./gradlew build          # compile + test (CI: ./gradlew build --no-daemon)
./gradlew test           # tests only (uses H2, no Postgres needed)
./gradlew bootRun        # needs a reachable Postgres (see env below)
```

## Architecture (`src/main/java/com/fantasy/db/`)

- `user/` — feature package:
  - `User` — JPA `@Entity` (UUID id, unique email, `password_hash`, `created_at`);
    use static `User.create(...)`.
  - `UserRepository` — `findByEmailIgnoreCase`, `existsByEmailIgnoreCase`
  - `UserService` — `@Transactional` create; throws `EmailAlreadyExistsException`
    (also catches `DataIntegrityViolationException` as a backstop)
  - `UserController` — `/api/v1/users`:
    - `GET /api/v1/users?email=` → user (404 if missing)
    - `GET /api/v1/users/exists?email=` → `{ "exists": bool }`
    - `POST /api/v1/users` → 201 created
  - `dto/` — `CreateUserRequest` (validated), `UserResponse`, `ExistsResponse`
- `exception/` — `EmailAlreadyExistsException`, `UserNotFoundException`,
  `ErrorDto`, `GlobalExceptionHandler`

## Database & config

- `application.yaml`: datasource
  `jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:fantasy}`,
  `ddl-auto: validate` (schema is owned by Flyway, **not** Hibernate),
  `server.port=${PORT:8086}`.
- Migrations live in `src/main/resources/db/migration/` (`V1__create_users_table.sql`).
  **Schema changes = a new `V__` migration**, never edit an applied one and never
  rely on Hibernate auto-DDL.
- Tests (`src/test/resources/application.yaml`): H2 in PostgreSQL mode,
  `ddl-auto: create-drop`, Flyway disabled.

## Conventions

- Feature-package layout (everything for an entity under one package).
- New entities: add Flyway migration + entity + repository + service + controller
  + DTOs, mirroring the `user` package.
- Keep endpoints under `/api/v1`.
- Never return raw entities with secrets to callers without thinking about
  exposure (see the auth warning above).

### OpenAPI annotations

This service's OpenAPI spec is consumed by `fantasy-bff` to generate a typed
HTTP client. Keeping annotations accurate is a first-class requirement.

- Annotate every controller with `@Tag(name = "...")`.
- Annotate every handler method with `@Operation(summary = "...")` and one
  `@ApiResponse` per distinct HTTP status it can return.
- Annotate every DTO record field with
  `@Schema(requiredMode = Schema.RequiredMode.REQUIRED)` unless the field is
  genuinely optional. This ensures the generated TypeScript client in
  `fantasy-web` gets non-nullable fields.
- Collections (`List`, `Set`) are always required — never leave them unannotated.
- After adding or changing an endpoint, verify Swagger UI at
  `http://localhost:8086/swagger-ui.html` reflects the change correctly before
  opening a PR.

**Current gap:** `UserController` has no `@Tag`, `@Operation`, or `@ApiResponse`
annotations, and DTO fields (`UserResponse`, `ExistsResponse`, `CreateUserRequest`)
have no `@Schema` annotations. These must be added before `fantasy-bff` can
generate a typed client to replace `HttpDatabaseServiceClient`.

## CI / workflow

- `.github/workflows/pr-checks.yml`: `./gradlew build --no-daemon` on PRs to `master`.
- `@claude` mentions on issues/PRs trigger `.github/workflows/claude.yml`.

See root `CLAUDE.md` for the PR merge convention and commit message rules.
