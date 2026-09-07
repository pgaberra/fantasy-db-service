# CLAUDE.md — fantasy-db-service

Persistence microservice for the fantasy hockey tool. Owns the database and
exposes a REST API for the BFF (`fantasy-bff`) to manage data: **users**, their
saved **projections**, and the public **shares** published from those projections.

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
  - `UserAvatar` — the account's **profile picture**, a `bytea` in its own `user_avatars` table
    (keyed by `user_id`, cascades on delete) rather than a column on `users`, since every sign-in
    reads the user row and none of those reads want a picture along with it. The BFF is trusted to
    have scaled the image and checked what it is; this service only bounds it (`image/png`, `jpeg`
    or `webp`, at most 512 KiB, `UserAvatar.MAX_BYTES`). `UserAvatarService` /
    `UserAvatarController` — `/api/v1/users/{userId}/avatar` (get 200/404, put, delete 204/404);
    the bytes travel as base64 in JSON (`AvatarResponse` / `SetAvatarRequest`).
  - `dto/` — `CreateUserRequest`, `GoogleUserRequest` (validated), `SetUsernameRequest`,
    `SetAvatarRequest`, `AvatarResponse`, `UserResponse`, `ExistsResponse`
- `projection/` — feature package (saved player projections, scoped to a user):
  - `UserProjection` — JPA `@Entity` (UUID id, `user_id`, `name`, `kind`, `season`, `data`,
    `created_at`, `updated_at`; unique `(user_id, name)` over everything but a preset
    draft, see `V19`). `data` is the **modelled,
    validated** `ProjectionData` (settings + per-player stats) stored in a **`jsonb`**
    column (`@JdbcTypeCode(SqlTypes.JSON)`). `season` is stamped from the
    `projections.current-season` config (the caller never sends it — not in
    `CreateProjectionRequest`); stored as the 8-digit code, exposed as the `Season` enum.
    `player_id_space` is the opposite: the caller **must** state it on create (`@NotNull`,
    no default), because only they know which platform's pool filled the rows, and a wrong
    value is silent until a remap translates ids that were never in the space it assumed.
    `kind` (`ProjectionKind`) separates the projection a user makes and edits
    (`PROJECTION`) from the one that only exists to hold a draft started from a preset
    such as last season's stats (`PRESET_DRAFT`), and from a board copied out of someone
    else's share link (`IMPORTED`) — a user may keep **one of each of the first two**, and
    only `PROJECTION` is their own work to list. Callers that show "my projections" filter
    on it; the service stores whichever kind the request asks for (defaulting to
    `PROJECTION`) and rejects a second of a kind that `isUniquePerUser()` with a 409.
    `IMPORTED` is deliberately not one of those: drafting against two friends' boards is a
    normal thing to want, and so is copying the **same** board twice, so nothing limits how
    many a user keeps. The name is what has to stay distinct, and `ProjectionImportService`
    settles that rather than refusing: with no `name` in the request it asks
    `UserProjectionService.freeNameFrom` for one, which is the shared name or `"… (2)"`,
    `"… (3)"` and so on — the same shape `V19` used to break the ties already in the table,
    and truncated the same way so the suffix fits the hundred characters a name gets. A name
    the **caller** chose is still refused with a 409 when it is taken: that one they can see
    and change. Repeat imports used to 409 either way, which left whoever pressed the button
    on a share page to go and sort the naming out themselves.
    An imported row is stamped with `origin_share_token` and `origin_author_username`
    (surfaced as `ProjectionOrigin` on both responses), snapshotted at import time so the
    credit survives the share going away.
  - `ProjectionData` — typed DTO: `settings` (`ProjectionSettings`) + `players`
    (`List<PlayerProjection>`). Per-player stats are validated **maps** (`stat → value`)
    keyed by the known stat vocabulary, so adding a stat needs no db-service change.
    `ProjectionSettings` also carries two fields this service only stores: `playerBasis`
    (what the rows started from — last season's stat line or zeros) and
    `playerPoolSyncedAt` (the sync run they were last squared with). The player pool
    changes under a saved projection all season, and the BFF is the one that can see it,
    so it reconciles the rows and writes both fields back; here they are just jsonb.
    `positionOverrides` (`List<PositionOverride>`) holds the positions the owner set by
    hand for individual skaters, because the platforms disagree about who is eligible
    where. It is keep-if-absent on update like the player rows, since several save paths
    send neither — an empty list, not a missing one, is how the app resets everyone back
    to the read model's positions — and `PlayerIdRemapService` remaps its ids along with the
    rest, since an override left on the old id would attach to whoever the new space
    numbers that way.
  - `Season` / `ScoringType` / `PlayerType` / `ProjectionKind` / `PlayerBasis` — enums with
    `@JsonValue` codes (`20262027`, `points`, `skater`, `preset_draft`, `last_season`).
  - `UserProjectionRepository` / `UserProjectionService` — CRUD scoped to the owning
    user (`findByIdAndUserId` enforces ownership; the unique constraint yields 409).
  - `UserProjectionController` — `/api/v1/users/{userId}/projections` (list/get/create/
    update/delete). List returns metadata only (no `data`).
  - `dto/` — `CreateProjectionRequest`, `UpdateProjectionRequest`, `ProjectionResponse`,
    `ProjectionSummaryResponse`, plus the `ProjectionData` model records.
- `share/` — feature package (a projection published under a public link):
  - `ProjectionShare` — JPA `@Entity` (UUID id, unique `projection_id`, `user_id`, unique
    `token`, `name`, `season`, `data`, timestamps). `data` is a
    **snapshot** (`SharedProjectionData` in a `jsonb` column): the settings and the ranked rows
    as they were when shared, so a link posted publicly keeps showing what was shared rather
    than whatever the owner edited afterwards. Those rows are the **whole board**, and they are
    the only copy of it — a shared row already carries everything a stored player row does (id,
    type, stats) on top of the identity and rank the public page renders, so an import derives
    its rows from `data` rather than from a second column. (It had one, `board`, until V17: back
    when `data` held a capped teaser, an import needed the full rows from somewhere else. How
    much of `data` a reader actually receives is now the BFF's call, not a question of what is
    stored.) A player-id remap rewrites these rows — left on the old numbering they would hand
    every later importer ids the player pool has forgotten.
  - The `token` is 16 random bytes from `SecureRandom`, base64url-encoded, not the projection's
    UUID. Publishing is **once and final**: sharing an already-shared projection returns the
    share it has, untouched, and there is no endpoint to refresh or withdraw one. Deleting the
    projection deletes the share with it (the row cascades) — that is the only thing that takes
    a link down.
  - `ProjectionShareService` — copies name, season, settings and `positionOverrides` from the
    stored projection so a client cannot publish a page that misrepresents it, and **strips
    `yahooSync`**: the owner's league name and key have no business on a public page. The ranked
    rows come from the caller, which owns the ranking, and carry denormalised identity (name,
    team, positions) so the public page renders without the player read model. The overrides
    travel *as well as* those positions, which already reflect them: the page renders off the
    rows, while an import needs to tell a correction from a position the read model reported,
    and a row cannot say which it is.
  - `ProjectionImportService` / `ProjectionImportController` —
    `POST /api/v1/users/{userId}/projections/imports`, which copies a share into the caller's
    own projections by its token. Anyone holding a token may import; the copy is of the frozen
    snapshot rather than the live projection behind it, because that snapshot is what the owner
    consented to publish. It carries no draft (the author's picks were theirs) and takes its
    season from the share, since the rows are that season's numbers. It **does** inherit the
    author's `positionOverrides`: the published ranking was computed against those positions, so
    a copy that moved players back onto the read model's would rank differently from the page it
    was copied from. The importer can undo them like any of their own. A link published before
    shares carried them inherits none, and starts on the reported positions.
  - `ProjectionShareController` — `/api/v1/users/{userId}/projections/{projectionId}/share`
    (get/put/delete, ownership-scoped). `SharedProjectionController` —
    `GET /api/v1/shares/{token}`, the snapshot the BFF serves publicly; it returns no owner
    identity beyond their public username, which is read **live** rather than snapshotted so a
    rename follows onto links already shared. It deliberately counts nothing: the share is fetched once for a
    chat client's link preview and again for its card, so a per-read counter measured crawlers
    rather than people (V13 dropped the column).
- `playerid/` — a one-off: rewriting stored player ids from one platform's numbering to
  another's. Yahoo stopped serving its player collection, ESPN provides the pool now, and the
  same people are numbered differently on the two.
  - `PlayerIdRemapService` — applies a crosswalk (old id → new id, computed by the BFF, which
    is the only place that can see both pools) to every projection and share still marked
    `player_id_space = 'yahoo'`, and stamps them `espn`. **Dry run by default**: an unqualified
    call reports and writes nothing. An id the crosswalk does not cover is **left as it is**,
    never dropped — a row the app cannot draw is invisible and recoverable, a deleted row is a
    user's work gone. The marker is what makes a second run safe: the two id spaces overlap in
    range, so re-running over an already-remapped row could translate an id that was never
    Yahoo's.
  - `PlayerIdRemapController` — `POST /api/v1/admin/player-ids/remap`.
- `exception/` — `ErrorDto`, `GlobalExceptionHandler`. The whole service uses **built-in**
  exceptions rather than custom ones (`NoSuchElementException` → 404,
  `IllegalArgumentException` → 400, `IllegalStateException` → 409,
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

### Error handling

The monorepo-wide rule (never silence an error; `ERROR` for 5xx, quiet for 4xx) lives in
the root `CLAUDE.md`. Specific here: built-in exceptions only, mapped as listed under
`exception/` above.

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

The full set lives in the monorepo root `CLAUDE.md`: input validation at every boundary,
logging & error handling, secrets only from env, one worktree per agent, and the merge
procedure. In short — the web talks only to the BFF; inter-service calls carry a shared
`X-Internal-Api-Key` header. Branch → push → PR → checks pass → **squash merge** to `master`
(the PR title becomes the commit message; make it a proper `feat:`/`fix:` message and merge
with an explicit `--subject`). No attribution trailers. Secrets only from env, never
committed — the local-dev DB password lives only in `docker-compose.yml`. Never merge a PR
titled "wip"/"draft".

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
