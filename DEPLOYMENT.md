# Deployment — fantasy-db-service (Render staging)

Deployed to Render as a **Docker web service** plus a **managed PostgreSQL**
database, both defined in [`render.yaml`](./render.yaml).

## How it runs

| Aspect | Value |
|---|---|
| Build | `Dockerfile` — JDK 25 builds the boot jar, JRE 25 runs it |
| Database | Render managed PostgreSQL (`fantasy-db`, free plan) |
| Schema | Flyway migrations run automatically on startup |
| Port | `${PORT}` (Render injects it); 8086 locally |
| Health check | `GET /actuator/health` |
| Auto-deploy | On every push to `master` |

The `DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD` env vars are wired automatically
from the managed database via `fromDatabase` references — you don't set them by hand.

`INTERNAL_API_KEY` must be set manually to the same value on both this service and
the BFF — see the security section below.

## First-time setup

1. Push `render.yaml` to `master`.
2. Render → **New → Blueprint** → connect `fantasy-db-service`. It creates **both**
   the `fantasy-db` Postgres instance and the `fantasy-db-service` web service.
3. Click **Apply**. The database provisions first, then the service builds and runs
   Flyway migrations on startup.
4. Generate a shared API key: `openssl rand -hex 32`
5. Set `INTERNAL_API_KEY` in the Render dashboard for **this service** and for
   **fantasy-bff** (both must be the same value). Redeploy both.
6. Verify: `https://fantasy-db-service.onrender.com/actuator/health` → `{"status":"UP"}`.

## Service-to-service security

All requests to `/api/**` must include the header `X-Internal-Api-Key: <secret>`.
Requests without it (or with the wrong key) receive `401 Unauthorized`. The
`/actuator/**` paths are always exempt so Render health checks work.

When `INTERNAL_API_KEY` is **not set** (e.g. local dev), the filter is disabled and
all requests are allowed — this makes local development easy without configuring keys.

### Why a shared key and not JWT/token auth like the BFF?

The BFF and this service authenticate **fundamentally different things**, so they use
different mechanisms on purpose:

- **The BFF authenticates *end users*.** Its JWT answers "who is this human and are
  they logged in?" — it carries a user identity, has a short expiry, and is minted
  after a password check. It's a session mechanism for people.
- **This hop authenticates *a service*, not a person.** When the BFF calls this
  service there often is no user at all (e.g. `register` and the existence check run
  *before* anyone is logged in), so a user-JWT model doesn't even fit. What this
  service needs to know is simply "is this call coming from my trusted BFF?" — a
  machine-to-machine trust question, which the shared key answers.

Duplicating the BFF's user-JWT auth here would also be the wrong layering: this
service is a deliberately dumb persistence layer that owns no business logic and no
concept of user sessions. Teaching it to validate user JWTs would couple it to the
BFF's signing secret and token format, give it knowledge that belongs to the BFF,
and *still* not cover the pre-login flows.

**This is not bulletproof** — it's a perimeter check, not strong auth. A shared
bearer secret has no per-request signing, no expiry, and manual rotation; anyone who
obtains the key can impersonate the BFF. Stronger future hardening options, in rough
order of effort: make this a **Render private service** (no public URL at all, key
becomes a second layer), adopt a **service-identity token** (client-credentials
OAuth2 or a signed service JWT, giving expiry + rotation), or **mTLS**. The shared
key is the pragmatic first step for a two-service internal mesh.

If per-user authorization is ever needed downstream (e.g. "fetch *my* roster"), the
intended pattern is for the BFF to **forward the user identity** (an `X-User-Id`
header or a propagated service token) *in addition to* the API key — not to move user
authentication into this service.

## Local development

```bash
# Start Postgres (from fantasy-db-service root)
docker compose up -d

# Run the service (no API key needed locally)
./gradlew bootRun
```

The service is then reachable at `http://localhost:8086`. Swagger UI:
`http://localhost:8086/swagger-ui.html`.

## Wiring the BFF to this service

The BFF reaches this service via `DATABASE_SERVICE_URL` (defaults to
`http://localhost:8086`). Run the BFF **without** the `mock` profile so it uses the
real HTTP client instead of the in-memory stub.

```bash
# From fantasy-bff root (local, no INTERNAL_API_KEY needed when both are local)
SPRING_PROFILES_ACTIVE=dev JWT_SECRET=$(openssl rand -base64 48) ./gradlew bootRun
```

## Free-tier caveats

- **Free Postgres expires after ~30 days** on Render and the service sleeps when
  idle (cold starts). Fine for staging; not for anything you care about keeping.
- Data is **not** backed up on the free plan.
