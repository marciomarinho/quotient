# ADR-0010: Single Keycloak realm + `tenant_id` claim

- Status: accepted
- Date: 2026-07-08

## Context

For the OAuth2 query path, tenants can be modelled as separate Keycloak realms
(strong isolation) or one realm with a `tenant_id` claim (shared). This mirrors
the pool/bridge/silo database trade-off (ADR-0004, docs/MULTI_TENANCY.md).

## Decision

Use a **single realm** (`quotient`) with a **`tenant_id` claim** per client
(protocol mapper from a client attribute). Realm roles (`tenant-admin`,
`tenant-viewer`, `platform-operator`) gate endpoints; the claim scopes data.

## Consequences

- Low operational cost: one realm, one set of keys to rotate; onboarding a tenant
  is adding a client + claim, not provisioning a realm.
- The `tenant_id` claim is the *only* source of tenant context on this path and
  feeds the same RLS chain, so a token for A cannot read B (tested).
- Trade-off: logical (not cryptographic) isolation between tenants; a realm-wide
  misconfiguration affects all. A tenant needing IdP-level isolation would be moved
  to its own realm — the same promotion path as the database silo model.
