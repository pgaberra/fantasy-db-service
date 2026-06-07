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

## CI / workflow

- `.github/workflows/pr-checks.yml`: `./gradlew build --no-daemon` on PRs to `master`.
- Branch → push → PR → checks pass → **squash merge** to `master`.
- `@claude` mentions on issues/PRs trigger `.github/workflows/claude.yml`.

### Merging PRs

GitHub squash merge uses the **PR title** as the commit message — the individual
branch commits are ignored. Before merging:

1. Ensure the PR title is a proper commit message (e.g. `feat: add X`, `fix: correct Y`).
   Rename it first with `gh pr edit <n> --title "..."` if needed.
2. Merge with an explicit subject so the commit message is never left to chance:
   ```
   gh pr merge <n> --squash --delete-branch \
     --subject "feat: describe the change (#<n>)" \
     --body "Optional longer description."
   ```

Never merge a PR titled "wip", "draft", or similar.

## Commit messages

End commit messages with:

```
Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
```
