# Keycloak realm-as-code

`quotient-realm.json` is imported at container startup (`start-dev --import-realm`).
It provisions the `quotient` realm with:

- **Roles:** `tenant-admin`, `tenant-viewer`, `platform-operator`.
- **Resource server:** `quotient-ledger` (validates JWTs on the ledger query/admin API).
- **Tenant service clients** (`client_credentials`): `tenant-acme`, `tenant-globex`,
  `tenant-initech`, each emitting a hardcoded `tenant_id` claim.
- **Demo operator client:** `quotient-demo-operator` (cross-tenant, used by `make demo`).

**Canonical demo tenant IDs** (reused by the seed script and the ledger RLS tests):

| Tenant | `tenant_id` |
|---|---|
| Acme    | `11111111-1111-1111-1111-111111111111` |
| Globex  | `22222222-2222-2222-2222-222222222222` |
| Initech | `33333333-3333-3333-3333-333333333333` |

Service-account role mappings and the human authorization-code walkthrough are
finalized in Phase 9a; see `docs/SECURITY.md`. Client secrets here are for local
development only.
