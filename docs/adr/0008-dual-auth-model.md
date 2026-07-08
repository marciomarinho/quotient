# ADR-0008: Dual auth — API keys on ingest, OAuth2 on query

- Status: accepted
- Date: 2026-07-08

## Context

Two very different traffic profiles hit the platform: high-volume
machine-to-machine telemetry, and lower-volume human/service queries of the
ledger. One auth mechanism serves neither well.

## Decision

Use **two mechanisms, chosen per profile** (see docs/SECURITY.md):

1. **Ingestion — API keys.** Per-tenant `Authorization: Bearer qk_live_...`,
   verified in-process against Argon2id hashes with a Caffeine cache. No IdP in the
   hot path, no token-refresh failure modes at 2k RPS; rotation via two active keys.
2. **Query/admin — OAuth2 + OIDC (Keycloak).** The ledger is a Spring Security
   resource server validating JWTs against the realm JWKS; roles from
   `realm_access.roles`; tenant from the `tenant_id` claim.

Both converge on the same `TenantContext` → `SET LOCAL app.tenant_id` chain.

## Consequences

- Each path uses the right tool; this matches how Stripe/OpenAI/Datadog split
  ingestion keys from dashboard OAuth.
- Trade-off: two mechanisms to reason about and secure, and two code paths that
  must agree on tenant context (they share `TenantContext`, tested).
