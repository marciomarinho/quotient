# ADR-0004: Pool multi-tenancy with PostgreSQL Row-Level Security

- Status: accepted
- Date: 2026-07-08

## Context

Thousands of tenants share infrastructure. We need strong data isolation without
a database (silo) or schema (bridge) per tenant. See docs/MULTI_TENANCY.md for the
full decision matrix.

## Decision

Use the **Pool** model: a shared schema with **PostgreSQL Row-Level Security**.
The app connects as a non-superuser role `quotient_app` and runs
`SET LOCAL app.tenant_id = '<uuid>'` per transaction; every tenant-scoped table
has a policy `USING (tenant_id = current_tenant_id())`. A missing setting resolves
to NULL and matches no rows (**fail closed**).

## Consequences

- Lowest cost/ops for many tenants; isolation is enforced by the database on every
  statement, not by hopeful application `WHERE` clauses. Proven by a test where a
  crafted query for tenant B returns zero rows under tenant A's binding.
- Tenant context comes only from the API key or JWT claim → `TenantContext` →
  `SET LOCAL` (never a request parameter).
- Trade-off: weaker *physical* isolation than silo/bridge, and an RLS-policy bug
  has realm-wide blast radius. A tenant demanding physical isolation is promoted to
  the bridge/silo model. Migrations run as the owner; the app role has least
  privilege (and no UPDATE/DELETE on the append-only ledger tables).
