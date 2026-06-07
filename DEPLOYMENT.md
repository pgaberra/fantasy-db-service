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

## First-time setup

1. Push `render.yaml` to `master`.
2. Render → **New → Blueprint** → connect `fantasy-db-service`. It creates **both**
   the `fantasy-db` Postgres instance and the `fantasy-db-service` web service.
3. Click **Apply**. The database provisions first, then the service builds and runs
   Flyway migrations on startup.
4. Verify: `https://fantasy-db-service.onrender.com/actuator/health` → `{"status":"UP"}`.

## Wiring the BFF to this service

The BFF reaches this service via `services.database.base-url` (see the BFF repo).
Point that at this service's URL and run the BFF **without** the `mock` profile so
it uses the real HTTP client instead of the in-memory stub.

## Free-tier caveats

- **Free Postgres expires after ~30 days** on Render and the service sleeps when
  idle (cold starts). Fine for staging; not for anything you care about keeping.
- Data is **not** backed up on the free plan.

## ⚠️ Security follow-up (do before storing real data)

This service is **public and unauthenticated** — anyone with the URL can read user
records (including password hashes) and create users. Before it holds real data,
add one of:
- A **shared secret / API key** header required on `/api/**`, set on both this
  service and the BFF, **or**
- Make it a **private service** not exposed to the public internet.
