# CLAUDE.md — fantasy-db-service

Persistence microservice for the fantasy hockey tool. Owns the database and
exposes a small REST API for the BFF (`fantasy-bff`) to manage data. v1 scope:
**users only** (create user, look up by email, existence check). Projections and
other entities will come later.

> This service is **internal-only**: deployed on Coolify with no public domain, and every
> `/api/**` request requires the shared `X-Internal-Api-Key` header (`INTERNAL_API_KEY`;
> missing/wrong → `401`). It returns password hashes to its trusted caller (the BFF), so
> that perimeter — no public route **plus** the API key — is what keeps them off the
> internet. It is a perimeter check between trusted services, **not** strong per-user
> auth; see `DEPLOYMENT.md` for the rationale and hardening options.

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
docker compose up -d     # start Postgres for local dev (defined in docker-compose.yml)
./gradlew bootRun        # run locally (requires Postgres via docker compose above)
```

## Architecture (`src/main/java/com/fantasy/db/`)

- `user/` — feature package:
  - `User` — JPA `@Entity` (UUID id, unique email, nullable `password_hash`, unique
    nullable `google_sub`, `created_at`); `User.create(...)` for password users,
    `User.createWithGoogle(...)` for Google users, `linkGoogle(...)` to attach Google to
    an existing account.
  - `UserRepository` — `findByEmailIgnoreCase`, `existsByEmailIgnoreCase`
  - `UserService` — `@Transactional` create; a duplicate email yields a
    `DataIntegrityViolationException` (→ 409), whether caught proactively or from the
    unique constraint on a race
  - `UserController` — `/api/v1/users`:
    - `GET /api/v1/users?email=` → user (404 if missing)
    - `GET /api/v1/users/exists?email=` → `{ "exists": bool }`
    - `POST /api/v1/users` → 201 created (password user)
    - `POST /api/v1/users/google` → 200; find-or-create-or-link for a verified Google
      identity (`{ email, googleSub }`). Resolves by `google_sub`, else links to an
      existing same-email account, else creates a password-less user.
  - `username` — the account's **public name**, nullable until the user picks one and unique
    regardless of case (a functional index on `LOWER(username)`, since "Alex" and "alex" read as
    the same name). `PUT /api/v1/users/{userId}/username` sets it; `[A-Za-z0-9_]{3,20}`. Sharing a
    projection requires it — that is the only thing that forces a name, so signing up does not.
  - `dto/` — `CreateUserRequest`, `GoogleUserRequest` (validated), `SetUsernameRequest`,
    `UserResponse`, `ExistsResponse`
- `projection/` — feature package (saved player projections, scoped to a user):
  - `UserProjection` — JPA `@Entity` (UUID id, `user_id`, `name`, `kind`, `season`, `data`,
    `created_at`, `updated_at`; unique `(user_id, kind, name)`). `data` is the **modelled,
    validated** `ProjectionData` (settings + per-player stats) stored in a **`jsonb`**
    column (`@JdbcTypeCode(SqlTypes.JSON)`). `season` is stamped from the
    `projections.current-season` config (the caller never sends it — not in
    `CreateProjectionRequest`); stored as the 8-digit code, exposed as the `Season` enum.
    `kind` (`ProjectionKind`) separates the projection a user makes and edits
    (`PROJECTION`) from the one that only exists to hold a draft started from a preset
    such as last season's stats (`PRESET_DRAFT`) — a user may keep **one of each**, and
    only the former is their own work to list. Callers that show "my projections" filter
    on it; the service stores whichever kind the request asks for (defaulting to
    `PROJECTION`) and rejects a second of the same kind with a 409.
  - `ProjectionData` — typed DTO: `settings` (`ProjectionSettings`) + `players`
    (`List<PlayerProjection>`). Per-player stats are validated **maps** (`stat → value`)
    keyed by the known stat vocabulary, so adding a stat needs no db-service change.
  - `Season` / `ScoringType` / `PlayerType` / `ProjectionKind` — enums with `@JsonValue`
    codes (`20262027`, `points`, `skater`, `preset_draft`).
  - `UserProjectionRepository` / `UserProjectionService` — CRUD scoped to the owning
    user (`findByIdAndUserId` enforces ownership; the unique constraint yields 409).
  - `UserProjectionController` — `/api/v1/users/{userId}/projections` (list/get/create/
    update/delete). List returns metadata only (no `data`).
  - `dto/` — `CreateProjectionRequest`, `UpdateProjectionRequest`, `ProjectionResponse`,
    `ProjectionSummaryResponse`, plus the `ProjectionData` model records.
- `share/` — feature package (a projection published under a public link):
  - `ProjectionShare` — JPA `@Entity` (UUID id, unique `projection_id`, `user_id`, unique
    `token`, `author_alias`, `name`, `season`, `data`, `view_count`, timestamps). `data` is a
    **snapshot** (`SharedProjectionData` in a `jsonb` column): the settings and the ranked rows
    as they were when shared, so a link posted publicly keeps showing what was shared rather
    than whatever the owner edited afterwards.
  - The `token` is 16 random bytes from `SecureRandom`, base64url-encoded — **not** the
    projection's UUID, so a link can be revoked. Unsharing deletes the row outright; sharing
    again mints a new token and the old link stays dead. Re-sharing an active share keeps the
    token (links already in the wild stay valid) and refreshes the snapshot.
  - `ProjectionShareService` — copies name, season and settings from the stored projection so a
    client cannot publish a page that misrepresents it, and **strips `yahooSync`**: the owner's
    league name and key have no business on a public page. The ranked rows come from the caller,
    which owns the ranking, and carry denormalised identity (name, team, positions) so the public
    page renders without the player read model.
  - `ProjectionShareController` — `/api/v1/users/{userId}/projections/{projectionId}/share`
    (get/put/delete, ownership-scoped). `SharedProjectionController` —
    `GET /api/v1/shares/{token}`, the snapshot the BFF serves publicly; it returns no owner
    identity beyond their public username, which is read **live** rather than snapshotted so a
    rename follows onto links already shared. It deliberately counts nothing: the share is fetched once for a
    chat client's link preview and again for its card, so a per-read counter measured crawlers
    rather than people (V13 dropped the column).
- `exception/` — `ErrorDto`, `GlobalExceptionHandler`. The whole service uses **built-in**
  exceptions rather than custom ones (`NoSuchElementException` → 404,
  `DataIntegrityViolationException` → 409, `MethodArgumentNotValidException` → 400).

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

- **No code comments unless asked.** Don't write code comments or documentation unless
  specifically asked to — prefer self-explanatory names. (Same AI guideline as the
  `fantasy-web` repo.)
- Feature-package layout (everything for an entity under one package).
- New entities: add Flyway migration + entity + repository + service + controller
  + DTOs, mirroring the `user` package.
- Keep endpoints under `/api/v1`.
- Never return raw entities with secrets to callers without thinking about
  exposure (see the security note above).

### Logging & error handling

**Never silence an error.** Every `@RestControllerAdvice` must have a catch-all
`@ExceptionHandler(Exception.class)` that **logs the full stack trace** (`log.error`)
and returns a consistent `ErrorDto` — an unmatched exception must never surface as an
opaque 500 with no server-side trace (this once made a downstream failure undiagnosable
in the BFF). Rules of thumb:

- **5xx / genuine faults** (unexpected exceptions, upstream/downstream call failures):
  log at `ERROR` with the exception so the stack trace is captured.
- **4xx / expected client outcomes** (not-found, conflict, validation): do **not** log
  as errors — they are normal and would just be noise.
- **Async / background work** (e.g. jobs on a virtual thread) does **not** reach the
  advice — it must `try/catch` and log its own failures at the job boundary.

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

### Spec snapshot (`specs/openapi.yaml`)

`specs/openapi.yaml` is a committed snapshot of the live OpenAPI spec, consumed by
`fantasy-bff` to generate its typed client. `OpenApiSpecSnapshotTest` boots the app
and asserts the committed spec matches the running one, so **any controller/DTO
change that isn't reflected in the spec fails the build**.

After an intentional API change, regenerate and commit:
```
./gradlew test -DupdateSpec=true   # rewrites specs/openapi.yaml
git add specs/openapi.yaml
```
The spec is LF-normalised (`.gitattributes`) so it diffs cleanly across OSes.

## CI / workflow

- `.github/workflows/pr-checks.yml`: `./gradlew build --no-daemon` on PRs to `master`.
- `@claude` mentions on issues/PRs trigger `.github/workflows/claude.yml`.

## Monorepo conventions

Shared across all four repos (`fantasy-web` → `fantasy-bff` → `fantasy-db-service` +
`fantasy-nhl-service`). The web talks only to the BFF; inter-service calls to db/nhl use a
shared `X-Internal-Api-Key` header.

### Input validation

**Every service validates its own inbound data independently** — never trust that an
upstream caller (e.g. the BFF) validated correctly. Reject malformed input at the
boundary with Bean Validation (`@Valid` on the controller param + `@NotBlank` / `@Email`
/ `@Size` / … on the DTO). **Every user-supplied string gets a `@Size(max=…)`** so an
oversized payload is rejected rather than processed or stored.

### Data hygiene (pre-launch)

**While the app is unreleased, fix the data — don't bend the code around it.**
When a failure is caused by stale / legacy / malformed *persisted* data (e.g. an
old jsonb shape that no longer deserializes), delete or correct the offending
data — a Flyway migration or one-off cleanup — rather than adding code that
tolerates it. Pre-launch the stored data is disposable, so a permanent code
accommodation that degrades the model (looser validation, unknown-field
tolerance, back-compat shims) is the wrong trade: it outlives the one-off
problem it solved. This calculus flips at launch, when real user data can no
longer be casually deleted and backward-compatible reads become legitimate.

Example: the draft-jsonb `by`-field incident was first patched with
`@JsonIgnoreProperties(ignoreUnknown = true)` (#43), then reverted in favour of
a migration that deletes the legacy drafts (#46).

### Secrets

**Never commit a password, API key, token, or any secret to git — in any environment**,
not even throwaway local-dev credentials, so the habit is absolute and we never risk
leaking (or reusing) a real one. Secrets come only from environment variables
(`${DB_PASSWORD}`, `${INTERNAL_API_KEY}`, …) — no literal value and **no default** in
`application*.yaml`; a missing var should fail fast, not fall back to a baked-in value.
Non-secret connection details (host, port, db name, username) may be committed. The
local-dev password lives only in `docker-compose.yml` (which defines the local DB). Run a
service against a chosen DB with the `local` / `staging` Spring profiles:
`SPRING_PROFILES_ACTIVE=<profile> DB_PASSWORD=… ./gradlew bootRun`.

### Merging PRs

Branch → push → PR → checks pass → **squash merge** to `master`. GitHub squash uses the
**PR title** as the commit message, so make it a proper message (`feat: …`, `fix: …`), then
merge with an explicit subject:
```
gh pr merge <n> --squash --delete-branch \
  --subject "feat: describe the change (#<n>)" \
  --body "Optional longer description."
```
Never merge a PR titled "wip"/"draft".

### Commit messages

No attribution trailers (`attribution.commit` / `attribution.pr` are `""` in
`~/.claude/settings.json`, enforced at the tool level).
