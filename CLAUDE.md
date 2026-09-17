# CLAUDE.md — fantasy-db-service

Persistence microservice for the fantasy hockey tool. Owns the database and
exposes a REST API for the BFF (`fantasy-bff`) to manage data: **users** (with their
avatars and password reset / email verification tokens), their saved **projections**, the
public **shares** published from those projections, and **premium** access (provider
subscriptions and admin grants).

> This service is **internal-only**: deployed on Coolify with no public domain, and every
> `/api/**` request requires the shared `X-Internal-Api-Key` header (`INTERNAL_API_KEY`;
> missing/wrong → `401`). It returns password hashes to its trusted caller (the BFF), so
> that perimeter — no public route **plus** the API key — is what keeps them off the
> internet. It is a perimeter check between trusted services, **not** strong per-user
> auth; see `DEPLOYMENT.md` for the rationale and hardening options.

## Tech stack

- Java 25, Spring Boot 4.1.1, Gradle (wrapper: `./gradlew`)
- Spring WebMVC on virtual threads, Spring Data JPA, Bean Validation, Actuator
- PostgreSQL 16 (runtime: the Coolify resources run `postgres:16-alpine` in staging and
  production, and `docker-compose.yml` runs the same image locally), Flyway migrations
- Tests: JUnit 5, H2 in-memory (PostgreSQL mode)
- springdoc OpenAPI / Swagger UI
- Sentry via `sentry-logback` (ERROR logs; inert unless `SENTRY_DSN` is set)
- SpotBugs + FindSecBugs (the build fails on any finding outside `config/spotbugs/exclude.xml`),
  JaCoCo coverage report

## Common commands

```bash
./gradlew build          # compile + SpotBugs + tests + JaCoCo (CI: ./gradlew build jacocoTestReport --no-daemon)
./gradlew test           # tests only (uses H2, no Postgres needed)
docker compose up -d     # start Postgres for local dev (defined in docker-compose.yml)
SPRING_PROFILES_ACTIVE=local DB_PASSWORD=… INTERNAL_API_KEY=… ./gradlew bootRun
```

`INTERNAL_API_KEY` is required locally too: without it the app refuses to start.

## Architecture (`src/main/java/com/fantasy/db/`)

- `config/`:
  - `InternalApiKeyFilter` — checks `X-Internal-Api-Key` and fails closed (no key configured →
    no startup). Exempts only `/actuator/health[/**]` and `/actuator/info`, matched on the
    decoded, normalised path so a traversal cannot slip a protected path past it.
  - `RequestBodyByteCountFilter` — counts the body bytes actually read, so a request body that
    stops arriving is logged with how far it got against its `Content-Length`.
- `user/` — feature package:
  - `User` — JPA `@Entity` (UUID id, unique email, nullable `password_hash`, unique
    nullable `google_sub` and `facebook_sub`, `username`, `email_verified`, `token_version`,
    `created_at`); `User.create(...)` for password users, `createWithGoogle(...)` /
    `createWithFacebook(...)` for social users, `linkGoogle(...)` / `linkFacebook(...)` to attach
    a provider to an existing account. A social sign-up starts verified; a password sign-up
    starts unverified until it consumes an email verification token. A password reset bumps
    `token_version`, so tokens issued before it can be rejected.
  - `UserRepository` — `findByEmailIgnoreCase`, `existsByEmailIgnoreCase`
  - `UserService` — `@Transactional` create; a duplicate email yields a
    `DataIntegrityViolationException` (→ 409), whether caught proactively or from the
    unique constraint on a race
  - `UserController` — `/api/v1/users`:
    - `GET /api/v1/users?email=` → user (404 if missing)
    - `GET /api/v1/users/{userId}` → user (404 if missing)
    - `GET /api/v1/users/exists?email=` → `{ "exists": bool }`
    - `POST /api/v1/users` → 201 created (password user)
    - `POST /api/v1/users/google` → 200; find-or-create-or-link for a verified Google
      identity (`{ email, googleSub }`). Resolves by `google_sub`, else links to an
      existing same-email account, else creates a password-less user.
    - `POST /api/v1/users/facebook` → 200; the same for a verified Facebook identity, by
      `facebook_sub`.
    - `POST /api/v1/users/{userId}/sessions/revoke` → 204; bumps `token_version`, which ends
      every session the account holds ("sign out everywhere"; the BFF refuses a refresh token
      issued under an older version). The only way a password-less account can do that.
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
  - `dto/` — `CreateUserRequest`, `GoogleUserRequest`, `FacebookUserRequest` (validated),
    `SetUsernameRequest`,
    `SetAvatarRequest`, `AvatarResponse`, `UserResponse`, `ExistsResponse`
- `projection/` — feature package (saved player projections, scoped to a user):
  - `UserProjection` — JPA `@Entity` (UUID id, `user_id`, `name`, `kind`, `preset`, `season`,
    `data`, `player_id_space`, `origin_share_token`, `origin_author_username`, `created_at`,
    `updated_at`; unique `(user_id, name)` over everything but a preset draft, and unique
    `(user_id, preset)` over preset drafts, see `V19`). `data` is the **modelled,
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
    else's share link (`IMPORTED`) — and only `PROJECTION` is their own work to list. Callers
    that show "my projections" filter on it; the service stores whichever kind the request asks
    for (defaulting to `PROJECTION`). Only `PRESET_DRAFT` is limited (`isUniquePerUser()`): **one
    per preset** (`ProjectionPreset`: `last_season` or `model`), and a second for the same preset
    is a 409. A user's own projections are unlimited, now that one can be started from a copy of
    another and kept beside it. `IMPORTED` never was limited: drafting against two friends'
    boards is a normal thing to want, and so is copying the **same** board twice. The name is
    what has to stay distinct, and **create settles a clash rather than refusing it**:
    `UserProjectionService.create` asks `freeNameFrom` for a name, which is the one the caller
    sent or `"… (2)"`, `"… (3)"` and so on — the same shape `V19` used to break the ties
    already in the table, and truncated the same way so the suffix fits the hundred characters
    a name gets. So the saved name is **the one in the response**, not necessarily the one that
    was sent, and a caller that shows it has to read it back. `ProjectionImportService` does the
    same for a board imported with no `name` (a name the **importer** typed is still refused —
    that one they can see and change). A **rename** is refused with a 409 too: there the name is
    the whole of what was asked for, and the page that asked can say so. Create used to 409 on a
    taken name, which threw away work a user had already done over something they could rename
    afterwards.
    An imported row is stamped with `origin_share_token` and `origin_author_username`
    (surfaced as `ProjectionOrigin` on both responses), snapshotted at import time so the
    credit survives the share going away.
  - `ProjectionData` — typed DTO: `settings` (`ProjectionSettings`) + `players`
    (`List<PlayerProjection>`). Per-player stats are validated **maps** (`stat → value`)
    keyed by the known stat vocabulary, so adding a stat needs no db-service change.
    `ProjectionSettings` also carries two fields this service only stores: `playerBasis`
    (what the rows started from — last season's stat line, zeros or the model's lines) and
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
    `manualRanking` holds, per player type, whether the rows are ordered by their projected
    stats or by the order the owner put them in, and the ids they placed by hand (best first,
    the rest falling in below them by projected value). A type left at `projected` is what a
    projection saved before this had. Like the overrides, its ids are remapped.
  - `Season` / `ScoringType` / `PlayerType` / `ProjectionKind` / `ProjectionPreset` /
    `PlayerBasis` / `PlayerIdSpace` — enums with `@JsonValue` codes (`20262027`, `points`,
    `skater`, `preset_draft`, `model`, `last_season`, `espn`).
  - `UserProjectionRepository` / `UserProjectionService` — CRUD scoped to the owning
    user (`findByIdAndUserId` enforces ownership; the unique constraint yields 409).
  - `UserProjectionController` — `/api/v1/users/{userId}/projections` (list/get/create/
    update/delete). List returns metadata only (no `data`).
  - `dto/` — `CreateProjectionRequest`, `UpdateProjectionRequest`, `ProjectionResponse`,
    `ProjectionSummaryResponse`, plus the `ProjectionData` model records.
- `share/` — feature package (a projection published under a public link):
  - `ProjectionShare` — JPA `@Entity` (UUID id, unique `projection_id`, `user_id`, unique
    `token`, `name`, `season`, `data`, `player_id_space`, timestamps). `data` is a
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
    stored projection so a client cannot publish a page that misrepresents it, and **strips**
    from the settings what is the owner's rather than the projection's: the Yahoo/ESPN sync
    details (league name and id, including the remembered `lastEspnLeagueId`), the player basis
    and pool sync stamp (meaningless on a frozen snapshot), and the new players the owner has not
    acknowledged. Sharing requires a username (`IllegalStateException` → 409). The ranked
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
    (get/put, ownership-scoped). `SharedProjectionController` —
    `GET /api/v1/shares/{token}`, the snapshot the BFF serves publicly; it returns no owner
    identity beyond their public username and the stamp on their profile picture
    (`authorAvatarUpdatedAt`, absent where they have none), both read **live** rather than
    snapshotted so a rename or a new picture follows onto links already shared.
    `GET /api/v1/shares/{token}/avatar` serves that picture, looked up by token alone so the
    caller serving the public page never learns whose account it is; the snapshot carries the
    stamp rather than the bytes, so half a megabyte of image stays out of every page load and the
    picture is cached under a URL that changes when it does. It deliberately counts nothing: the share is fetched once for a
    chat client's link preview and again for its card, so a per-read counter measured crawlers
    rather than people (V13 dropped the column).
- `passwordreset/` and `emailverification/` — single-use tokens:
  `POST /api/v1/users/{password-reset|email-verification}/tokens` issues one,
  `POST /api/v1/users/{password-reset|email-verification}` consumes it (unknown, expired or used
  → 404). Only the SHA-256 hash is stored, and issuing replaces any outstanding token. Issuing
  for an account that cannot use one (unknown email; for a reset, a password-less account; for
  verification, one already verified) issues nothing, so the BFF can answer identically either
  way. Lifetimes: `security.password-reset.token-ttl` (default `PT30M`) and
  `security.email-verification.token-ttl` (default `P1D`).
- `subscription/` — one provider subscription per user (`Subscription`, statuses in
  `SubscriptionStatus`). `PUT /api/v1/users/{userId}/subscription` upserts it from a provider
  event and **ignores an event older than the stored `last_event_at`**, so out-of-order webhooks
  cannot roll a subscription back; `GET` reads it (404 if none).
- `premium/` — feature package (who has premium access):
  - `PremiumGrant` — premium handed out by an admin rather than paid for, in its own table so a
    provider webhook and a grant can never overwrite each other. Rows are kept and revoking sets
    `revoked_at`, so who gave what, to whom and why stays readable. A user may hold several; the
    one that lasts longest is the one that counts.
  - `PremiumService` — resolves a subscription and any live grant into one answer
    (`PremiumEntitlementResponse`: `premium`, `source`, `premiumUntil`), and lists everyone who
    has premium right now (`PremiumCustomerResponse`) for the BFF's admin view.
  - `UserPremiumController` — `/api/v1/users/{userId}/premium` (entitlement),
    `/premium/grants` (list, create, revoke-all). `PremiumCustomerController` —
    `GET /api/v1/premium/customers`.
- `playerid/` — a one-off: rewriting stored player ids from one platform's numbering to
  another's. Yahoo stopped serving its player collection, ESPN provides the pool now, and the
  same people are numbered differently on the two.
  - `PlayerIdRemapService` — applies a crosswalk (old id → new id, computed by the BFF, which
    is the only place that can see both pools) to every projection and share still marked
    `player_id_space = 'yahoo'`, and stamps them `espn`. **Dry run by default**: an unqualified
    call reports and writes nothing. An id the crosswalk does not cover is **left as it is**,
    never dropped — a row the app cannot draw is invisible and recoverable, a deleted row is a
    user's work gone. The one exception is an uncovered id that another player is being moved
    **to** (Yahoo's 5738 was Martin Frk, ESPN's is Brian Dumoulin): kept, that row would be drawn
    as the other player, so it is removed along with its overrides and notices, and counted as
    `colliding`. A draft pick like that is never removed, since that would rewrite the draft; any
    at all make an apply refuse with 409. The marker is what makes a second run safe: the two id spaces overlap in
    range, so re-running over an already-remapped row could translate an id that was never
    Yahoo's.
  - `PlayerIdRemapController` — `POST /api/v1/admin/player-ids/remap`.
- `exception/` — `ErrorDto`, `GlobalExceptionHandler`. The whole service uses **built-in**
  exceptions rather than custom ones (`NoSuchElementException` → 404,
  `IllegalArgumentException` → 400, `IllegalStateException` → 409,
  `DataIntegrityViolationException` → 409, `MethodArgumentNotValidException` → 400,
  `NoResourceFoundException` → 404 for a path this build does not serve).

## Database & config

- `application.yaml`: datasource
  `jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:fantasy}` with
  `${DB_USER:fantasy}` / `${DB_PASSWORD}` (no default), `ddl-auto: validate` (schema is owned
  by Flyway, **not** Hibernate), `internal.api-key: ${INTERNAL_API_KEY:}` (blank refuses to
  start), `server.port=${PORT:8086}`. Profiles: `application-local.yaml` (docker-compose
  Postgres) and `application-staging.yaml`.
- Migrations live in `src/main/resources/db/migration/` (`V1__create_users_table.sql` onward).
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
`exception/` above. A caller that goes away (a `ClientAbortException` or
`AsyncRequestNotUsableException` in the cause chain) is logged at `WARN`, not `ERROR`:
mid-response with nothing sent, mid-request with a 400 and the bytes read against the declared
length. Any other failure to read or write a body stays an `ERROR`, because every caller is one
of our own services and JSON we cannot parse is our bug.

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

- `.github/workflows/pr-checks.yml`: `./gradlew build jacocoTestReport --no-daemon` on PRs to
  `master`.
- `tag-on-merge.yml`: every merge to `master` tags a version, creates a **draft** GitHub Release
  and stamps that version on staging.
- `promote-to-prod.yml`: **publishing** the draft release promotes it to production; a failed
  promotion opens a `prod-promotion-failed` issue.
- `qodana.yml`: weekly, Mondays at 06:00 UTC (`schedule`), and on demand (`workflow_dispatch`).

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
