# Decision log

Non-obvious choices in fantasy-db-service, one line each. The format and rules are in the monorepo
root's `DECISIONS.md`; choices that span repos go there.

---

[2026-09-11] fantasy-db-service: a premium grant truncates its instants to microseconds in the entity, so it holds exactly what `TIMESTAMPTZ` stores (Postgres and H2 both round half up, so an instant ending in ≥500 ns was stored after itself) — rejected: an injected `Clock` ticking in microseconds, which covers only the instants the service stamps and not a caller's `expiresAt`; and changing every entity, since grants are the only rows queried against a freshly stamped `now`.
