# Deployment — fantasy-db-service

Deployed via **Coolify** (self-hosted on Hetzner) as a **Docker service** plus a
dedicated **PostgreSQL** database (one Coolify Postgres per service). It is an
**internal-only** service — no public domain; the BFF reaches it over the internal
Docker network via its stable alias `db-service:8086`.

## How it runs

| Aspect | Value |
|---|---|
| Build | `Dockerfile` — JDK 25 builds the boot jar, JRE 25 runs it |
| Database | Dedicated Coolify PostgreSQL (`postgres:16-alpine`), on the same Docker network |
| Schema | Flyway migrations run automatically on startup (`ddl-auto: validate`) |
| Port | `${PORT}` (defaults to 8086); reached internally as `db-service:8086` |
| Health check | `GET /actuator/health` |
| Network alias | `db-service` (Coolify `custom_network_aliases`, so the BFF URL is stable) |

## Environment variables (set in Coolify, per environment)

| Key | Value |
|---|---|
| `DB_HOST` | The Postgres resource's container name on the `coolify` network (its UUID alias) |
| `DB_PORT` | `5432` |
| `DB_USER` | `postgres` |
| `DB_NAME` | `postgres` |
| `DB_PASSWORD` | The password Coolify generated for that Postgres (read from the DB resource) |
| `INTERNAL_API_KEY` | Shared secret for BFF → this service. **Same value** as the BFF's `DB_INTERNAL_API_KEY`. Generate with `openssl rand -hex 32`. |

> `INTERNAL_API_KEY` and the DB password are **environment-specific** — staging and prod
> use independent secrets, never shared.

## First-time setup (per environment)

1. In Coolify, create a **PostgreSQL** resource on the target server/environment. Note
   its container/UUID (→ `DB_HOST`) and generated password (→ `DB_PASSWORD`).
2. Create an application from this repo (GitHub App source, **Dockerfile** build pack) on
   the same server/environment. Set `custom_network_aliases` to `db-service`.
3. Set the env vars above (no public domain needed) and deploy. Flyway runs the
   migrations on startup.
4. Set the **same** `INTERNAL_API_KEY` value as the BFF's `DB_INTERNAL_API_KEY`, and the
   BFF's `DATABASE_SERVICE_URL` to `http://db-service:8086`.
5. Verify: `GET /actuator/health` → `{"status":"UP"}` (reachable from the BFF container or
   the server over the Docker network).

## Service-to-service security

All requests to `/api/**` must include the header `X-Internal-Api-Key: <secret>`.
Requests without it (or with the wrong key) receive `401 Unauthorized`. Only the
`/actuator/health` and `/actuator/info` probes are exempt so health checks work.

`INTERNAL_API_KEY` is **required in every environment**: the service **refuses to start**
if it is blank (fail closed) rather than serving an unauthenticated API — this service
returns password hashes. Set it when running locally too (like `DB_PASSWORD`); tests supply
their own key.

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
obtains the key can impersonate the BFF. It is mitigated by the service having **no
public domain** (internal-network only). Stronger future hardening options, in rough
order of effort: a **service-identity token** (client-credentials OAuth2 or a signed
service JWT, giving expiry + rotation), or **mTLS**. The shared key is the pragmatic
first step for a two-service internal mesh.

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
`http://localhost:8086`; on Coolify it is `http://db-service:8086`).

```bash
# From fantasy-bff root (local, no INTERNAL_API_KEY needed when both are local)
SPRING_PROFILES_ACTIVE=dev JWT_SECRET=$(openssl rand -base64 48) ./gradlew bootRun
```
