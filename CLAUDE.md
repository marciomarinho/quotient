# CLAUDE.md — working notes for this repository

Quotient is a real-time usage-metering & monetization platform: ingest token
telemetry → stream through Kafka → aggregate into meter readings → rate against
per-tenant plans → post to a double-entry ledger, with strict multi-tenant
isolation at every layer. See `quotient-project-plan.md` for the full spec and
`docs/` for design detail.

## Module map

| Module | Role |
|---|---|
| `quotient-common` | Domain records, `Money` type, event schemas, tenant context. Framework-free. |
| `ingest-gateway-vthreads` | Ingestion API — Spring MVC + virtual threads (Loom). |
| `ingest-gateway-reactive` | Ingestion API — Spring WebFlux + reactor-kafka. Same external contract. |
| `meter-aggregator` | Kafka Streams: tumbling windows, exactly-once v2, late events. |
| `rating-engine` | Applies flat/tiered/volume pricing; emits charges. Deterministic. |
| `ledger-service` | Double-entry ledger + invoicing + tenant-scoped query API (Postgres, RLS). |
| `loadtest` | k6 scenarios + report generator for the gateway benchmark. |

Build is a Gradle (Kotlin DSL) monorepo. All shared build config lives in one
convention plugin: `build-logic/src/main/kotlin/quotient.java-conventions.gradle.kts`
(the "parent POM"). Dependency versions are in `gradle/libs.versions.toml`.

## Key invariants (do not violate)

- **Money is `long` minor units end-to-end.** `BigDecimal` only at
  unit-price × quantity in rating, with `HALF_EVEN`, immediately back to minor
  units. No `double`/`float` for money — ArchUnit enforces this.
- **Ledger is append-only.** Entries table has `UPDATE`/`DELETE` revoked;
  per-transaction `SUM(debits) == SUM(credits)` enforced by a deferred DB
  trigger, the app posting service, and consumer idempotency.
- **Tenant context is mandatory** on every tenant-scoped operation: API key /
  JWT claim → `TenantContext` → Kafka key → Postgres `SET LOCAL app.tenant_id`
  under RLS. Never derive tenant from a request parameter.

## Commands

```
make up            # start local infra          make build   # compile + assemble
make down / down-v # stop (keep / drop volumes)  make test    # unit tests
make itest         # Testcontainers integration  make coverage# aggregate JaCoCo
make lint / format # Spotless + Checkstyle        make demo    # end-to-end (Phase 7+)
make bench         # gateway benchmark (Phase 10) make ledger-verify
```

Requires JDK 25 (Temurin/GraalVM), Docker, and `k6`. Gradle runs via `./gradlew`
(Gradle 9.6.1; runs on JDK 25).

## Conventions

- Package root `io.github.marciomarinho.quotient`.
- Conventional commits; commit per logical step.
- ADR before any big decision (`docs/adr/`, MADR template).
- **Constructor injection only** (no field `@Autowired`).
- **Ports-and-adapters:** domain packages never import Spring/Kafka/JDBC types
  (ArchUnit-enforced). Adapters live at the edges.
- **Sealed interfaces** for closed hierarchies (e.g. pricing models).
- Immutability by default (records, `List.copyOf`); no `null` returns in domain
  APIs — use `Optional` or throw.
- Tests are given/when/then with behavioral assertions and meaningful failure
  messages. Coverage is a floor, not a target; a test without a meaningful
  assertion is a defect.

## Never do

- Floating-point money.
- Cross-tenant queries without RLS context.
- Blocking calls in the reactive module (BlockHound-enforced).
- Unbounded in-memory buffering (backpressure → `503`, never OOM).
- Field injection; `*Util` / `*Manager` god classes.
- Exclude domain code from JaCoCo to pass a coverage gate.
- Tests without meaningful assertions.
