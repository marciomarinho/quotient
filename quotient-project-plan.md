# Quotient — Real-Time Usage Metering & Monetization Platform

> **Repository:** `github.com/marciomarinho/quotient`
> **Owner:** marciomarinho (personal project)
> **License:** Apache-2.0
> **Tagline:** *Token-level usage metering, rating, and ledger-grade billing — built for scale, runnable on a laptop.*

This document is the complete specification for Claude Code to implement. Read it fully before writing any code. Work through the phases in order (Section 16), committing incrementally with conventional commits.

---

## 1. Purpose of This Project

Quotient is a portfolio-grade demonstration of production monetization infrastructure. It shows, end-to-end, how a modern usage-based billing platform works:

1. **Ingest** token-level telemetry (e.g., AI API usage events) through a high-concurrency HTTP API.
2. **Stream** events through Kafka with per-tenant partitioning and exactly-once processing semantics.
3. **Aggregate** raw events into billable meter readings (windowed, late-event tolerant).
4. **Rate** aggregated usage against per-tenant pricing plans (tiered, volume, flat).
5. **Record** every monetary movement in a double-entry ledger with strict integrity invariants.
6. **Isolate** tenants at every layer: API, Kafka, database (PostgreSQL Row-Level Security).

It additionally contains a rigorous, benchmarked comparison of two ingestion gateway implementations:

- **Spring Boot WebFlux** (reactive, event-loop model)
- **Spring Boot MVC + Virtual Threads** (Java 25, Project Loom)

Everything runs locally with a single `docker compose up` / `make up`. There are no cloud dependencies.

## 2. Goals and Non-Goals

### Goals
- Demonstrate architectural judgement: clear service boundaries, explicit trade-off documentation (ADRs), C4 diagrams.
- Correctness under concurrency: idempotent ingestion, exactly-once aggregation, ledger invariants that cannot be violated.
- Honest performance engineering: reproducible load tests, real numbers, a written comparison with conclusions.
- Multi-tenancy done properly: tenant context propagation, RLS enforcement, per-tenant rate limiting, noisy-neighbor protection.
- Excellent documentation: a reader should understand the problem, the solution, and the trade-offs without running the code.

### Non-Goals
- No payment provider integration (Stripe etc.). Invoicing stops at "invoice generated + ledger posted."
- No UI beyond Grafana dashboards and a minimal static demo page (optional stretch).
- No Kubernetes. Docker Compose only.
- No social login / external IdP federation. Identity is local Keycloak only; ingestion uses API keys by design (see Section 14a).

## 3. The Problem (to be explained in docs/PROBLEM.md)

Modern API-first companies (especially AI companies) bill on consumption: tokens, requests, GB, compute-seconds. This creates hard engineering problems:

1. **Volume:** a single customer can emit millions of usage events per day. Ingestion must be cheap, fast (p99 < 50 ms locally), and non-blocking.
2. **Correctness:** billing errors destroy trust. Events must never be double-counted (idempotency) or lost (durability), and monetary math must be exact (no floating point).
3. **Multi-tenancy:** thousands of customers share infrastructure. One tenant must never see another's data, and a spiking tenant must not degrade others.
4. **Auditability:** every invoice line must be traceable back to raw events; every dollar must be explainable via a double-entry ledger.
5. **Timeliness:** customers want near-real-time visibility of spend (spend caps, alerts), which conflicts with batch-oriented billing pipelines.

Claude Code: write `docs/PROBLEM.md` explaining this in depth with a Mermaid diagram of the naive approach (writing usage rows straight to a DB) and why it collapses at scale (hot rows, lock contention, no replay, no audit trail).

## 4. High-Level Architecture

```
                          ┌────────────────────────────┐
  usage events (HTTP) ──▶ │ ingest-gateway             │  two interchangeable impls:
  (token telemetry)       │  • reactive (WebFlux)      │  same API contract, same tests
                          │  • vthreads (MVC + Loom)   │
                          └──────────┬─────────────────┘
                                     │ validated + enriched events
                                     ▼
                          ┌────────────────────────────┐
                          │ Kafka (KRaft, 1 broker)    │
                          │ topic: usage.events.v1     │
                          │ key = tenantId             │
                          └──────────┬─────────────────┘
                                     ▼
                          ┌────────────────────────────┐
                          │ meter-aggregator           │  Kafka Streams
                          │ tumbling windows,          │  exactly-once v2
                          │ late-event grace period    │
                          └──────────┬─────────────────┘
                                     │ topic: usage.aggregates.v1
                                     ▼
                          ┌────────────────────────────┐
                          │ rating-engine              │  consumes aggregates,
                          │ tiered/volume/flat pricing │  produces charges
                          └──────────┬─────────────────┘
                                     │ topic: billing.charges.v1
                                     ▼
                          ┌────────────────────────────┐
                          │ ledger-service             │  double-entry ledger in
                          │ + invoice generation       │  PostgreSQL (RLS enabled)
                          │ + query API                │
                          └────────────────────────────┘

  Cross-cutting: Prometheus + Grafana, tenant-config in Postgres,
  k6 load-test harness, Testcontainers integration tests.
```

Claude Code: reproduce this as proper **Mermaid C4 diagrams** (Context, Container, and one Component diagram for the ledger) in `docs/ARCHITECTURE.md`. Also produce Mermaid **sequence diagrams** for: (a) event ingestion happy path, (b) duplicate event rejection, (c) window close → rating → ledger posting.

## 5. Domain Model

Package root: `io.github.marciomarinho.quotient`.

| Entity | Key fields | Notes |
|---|---|---|
| Tenant | id (UUID), name, apiKeyHash, status, plan | Seeded: 3 demo tenants |
| Meter | id, tenantScope, code (e.g. `llm.tokens.input`), aggregation (SUM/COUNT/MAX), unit | Global catalog + per-tenant overrides |
| UsageEvent | idempotencyKey (client-supplied), tenantId, meterCode, quantity (long), dimensions (map, e.g. model=..., region=...), occurredAt, receivedAt | Immutable |
| MeterReading | tenantId, meterCode, windowStart, windowEnd, quantity, eventCount | Output of aggregator |
| PricePlan / PriceTier | plan → meter → tiers (upTo, unitPriceMinor) | Prices in **minor units (long)**, currency AUD |
| Charge | tenantId, meterCode, window, quantityBilled, amountMinor, planVersion | Output of rating |
| LedgerAccount | tenantId, type (RECEIVABLE, REVENUE, CREDITS, TAX) | |
| LedgerEntry / LedgerTransaction | transaction groups ≥2 entries; SUM(debits) == SUM(credits) enforced | Append-only |
| Invoice / InvoiceLine | generated from charges for a period; links to ledger transaction | |

**Money rule:** all monetary amounts are `long` minor units end-to-end. `BigDecimal` allowed only at unit-price × quantity rating calculations, with explicit rounding mode (HALF_EVEN), immediately converted back to minor units. No `double`/`float` for money anywhere — add an ArchUnit test enforcing this.

## 6. Repository Layout (Gradle multi-module monorepo)

```
quotient/
├── CLAUDE.md
├── README.md
├── Makefile
├── docker-compose.yml
├── settings.gradle.kts / build.gradle.kts (version catalog: gradle/libs.versions.toml)
├── quotient-common/            # domain records, event schemas, tenant context, money type
├── ingest-gateway-reactive/    # Spring WebFlux implementation
├── ingest-gateway-vthreads/    # Spring MVC + virtual threads implementation
├── meter-aggregator/           # Kafka Streams app
├── rating-engine/              # plain Spring Boot Kafka consumer/producer
├── ledger-service/             # Spring Boot + Postgres (jOOQ or Spring Data JDBC), REST query API
├── loadtest/                   # k6 scripts + result parsing + report generator
└── docs/
    ├── PROBLEM.md
    ├── ARCHITECTURE.md          # C4 + sequence diagrams (Mermaid)
    ├── MULTI_TENANCY.md         # isolation models compared; what we chose and why
    ├── SECURITY.md              # dual-auth model, Keycloak tenant mapping, threat model
    ├── LEDGER_DESIGN.md         # double-entry design, invariants, outbox pattern
    ├── EXACTLY_ONCE.md          # idempotency + EOS semantics across the pipeline
    ├── BENCHMARK.md             # reactive vs virtual threads: method, results, analysis
    └── adr/                     # ADR-0001..N (template: MADR)
```

## 7. Ingestion Gateway — Contract (both implementations MUST be identical externally)

`POST /v1/usage/events` (single) and `POST /v1/usage/events:batch` (up to 1,000 events).

- Auth: `Authorization: Bearer <tenant API key>` → resolves tenant, sets tenant context.
- Validation: schema, meter exists, quantity ≥ 0, occurredAt not > 5 min in the future.
- **Idempotency:** `idempotencyKey` per event. Deduplicate with a Redis `SET NX EX 86400` (or Caffeine + Redis tiered) check *before* Kafka publish; duplicates return `202` with `"deduplicated": true`. Document the trade-off: dedup window vs storage; Kafka EOS handles downstream.
- **Backpressure:** if Kafka is unavailable, return `503` with `Retry-After`. Never buffer unbounded in memory.
- **Per-tenant rate limiting:** token bucket (Bucket4j), configurable per tenant, `429` on breach. This is the noisy-neighbor story.
- Response p99 target locally: < 50 ms at 2,000 RPS sustained.
- Publish to Kafka with `key = tenantId`, `acks=all`, idempotent producer enabled.
- Both gateways share contract tests from `quotient-common` (Spring Cloud Contract or a shared REST-assured test suite run against each).

**Implementation-specific requirements:**
- *Reactive:* WebFlux + reactor-kafka; no blocking calls on event loop (enforce with BlockHound in tests).
- *Virtual threads:* Spring MVC, `spring.threads.virtual.enabled=true`, standard KafkaTemplate; document pinning risks (synchronized blocks in old libs) and verify with JFR `jdk.VirtualThreadPinned` events during load tests.

## 8. Meter Aggregator (Kafka Streams)

- Tumbling windows of 1 minute (configurable), grace period 30 s for late events; suppress until window close.
- `processing.guarantee=exactly_once_v2`.
- Group by (tenantId, meterCode, dimensions-hash); aggregation per meter definition.
- Emits `MeterReading` to `usage.aggregates.v1`.
- State stores backed by RocksDB (default); document rebalancing/standby considerations even though single-instance locally.
- Interactive Queries endpoint (optional stretch): `GET /v1/tenants/{id}/usage/live`.

## 9. Rating Engine

- Consumes aggregates; loads the tenant's plan (cached, versioned — a rating must record `planVersion` used).
- Supports **flat**, **tiered** (graduated), and **volume** pricing. Include unit tests with a table of worked examples in the test names/docs.
- Produces `Charge` records to `billing.charges.v1`.
- Deterministic: re-processing the same aggregate with the same plan version must produce an identical charge (property-based test with jqwik).

## 10. Ledger Service — the integrity centerpiece

- PostgreSQL 17. Schema owned via Flyway migrations.
- **Double-entry:** every charge posts a transaction: DEBIT `tenant.RECEIVABLE` / CREDIT `tenant.REVENUE` (+ TAX split, 10% GST, as a second entry pair).
- Invariants enforced at **three layers** (document this explicitly in LEDGER_DESIGN.md):
  1. DB constraint: deferred constraint trigger asserting per-transaction debits == credits; entries table is append-only (REVOKE UPDATE/DELETE).
  2. Application: transactional posting service, SERIALIZABLE isolation with retry-on-40001.
  3. Consumer idempotency: charges carry a deterministic ID; posting is `INSERT ... ON CONFLICT DO NOTHING` on transaction id — replaying Kafka never double-posts.
- **Multi-tenant isolation:** RLS policies on every tenant-scoped table; the app connects as a non-superuser role and sets `SET LOCAL app.tenant_id` per transaction. Add a test proving cross-tenant reads return zero rows even with a crafted query.
- Invoice generation: `POST /v1/tenants/{id}/invoices?period=...` aggregates charges into an invoice + posts the ledger transaction atomically (outbox pattern for the `invoice.created` Kafka event — document why outbox, with a Mermaid diagram).
- Query API: account balances, transaction history, invoice detail — all tenant-scoped.
- A `quotient ledger verify` task (Gradle or CLI) that re-computes all balances from entries and asserts they match materialized balances. Run it in CI.

## 11. Multi-Tenancy Documentation Requirement (docs/MULTI_TENANCY.md)

Compare the three canonical models with a decision matrix (isolation, cost, ops complexity, blast radius, per-tenant scale ceiling):
1. Silo (DB per tenant)
2. Bridge (schema per tenant)
3. Pool (shared schema + RLS) ← **implemented here**

Explain the layered isolation actually implemented: API key → tenant context → per-tenant rate limits → Kafka partitioning by tenant → RLS. Include a Mermaid diagram showing tenant context propagation across the whole pipeline (HTTP header → Kafka record key/headers → `SET LOCAL` in Postgres).

## 12. Benchmark: WebFlux vs Virtual Threads (docs/BENCHMARK.md)

Methodology (must be reproducible with `make bench`):
- k6 scenarios against each gateway, identical Docker resource limits (e.g., 2 CPU / 1 GB via compose `deploy.resources` or `cpus:`/`mem_limit`).
- Scenarios: (a) sustained 500/1000/2000 RPS single events, (b) batch ingestion, (c) spike test 0→3000 RPS, (d) slow-downstream test (inject 50 ms Kafka publish latency via Toxiproxy) — this is where the models genuinely diverge.
- Collect: throughput, p50/p95/p99 latency, error rate, CPU, RSS memory, GC pauses (via JFR), thread/carrier-thread counts, virtual-thread pinning events.
- Output: k6 JSON → a small parser in `loadtest/` producing Markdown tables + PNG charts (Python matplotlib or vega-lite) embedded in BENCHMARK.md.
- The write-up must include an honest **analysis and recommendation**: code complexity, debuggability (stack traces!), library ecosystem constraints (BlockHound vs pinning), and when each model wins. No marketing conclusions — report what the numbers actually show.

## 13. Local Environment

`docker-compose.yml` services: kafka (KRaft single node, e.g. apache/kafka or bitnami), postgres:17, redis:7, prometheus, **grafana + tempo** (Grafana provisioned with both Prometheus and Tempo datasources as code), otel-collector (contrib image, OTLP in → Tempo + Prometheus out), **keycloak** (realm imported from `infra/keycloak/realm-export.json` at startup — realm-as-code, no manual clicking), toxiproxy, kafka-ui (provectus) for demo visibility. All app services also containerized with multi-stage Dockerfiles (Temurin 25 JRE, non-root user, layered jars).

`Makefile` targets: `up`, `down`, `build`, `test`, `itest`, `coverage` (aggregate JaCoCo HTML report), `seed` (3 tenants + plans + meters), `demo` (script that fires realistic traffic and prints an invoice at the end), `bench`, `ledger-verify`, `lint`.

`make demo` end-to-end acceptance: seed → send 10k events across 3 tenants (with 5% deliberate duplicates) → wait for windows → generate invoices → print each tenant's invoice + ledger balances → assert duplicates were not billed and debits == credits. This script doubles as the smoke test.

## 14. Observability

- Micrometer → Prometheus on every service; RED metrics per endpoint, per-tenant tagged counters (bounded cardinality: tag only the 3 demo tenants).
- Grafana dashboards (JSON provisioned): "Ingestion" (RPS, latency percentiles, dedup rate, 429s per tenant), "Pipeline" (consumer lag, window emissions, rating throughput), "Ledger" (postings/s, balance totals).
- Structured JSON logging (logback), `tenantId` and `traceId` in MDC.
- **Distributed tracing (OpenTelemetry → Grafana Tempo):**
  - Every service instrumented with the OTel Java agent (or Micrometer Tracing + OTel bridge — pick one, record the choice and trade-off in an ADR; the agent gives zero-code Kafka/JDBC/Lettuce spans, the bridge gives cleaner custom spans and no agent overhead in benchmarks).
  - Export OTLP → otel-collector → Tempo. Grafana Tempo datasource provisioned as code with **trace-to-logs** and **trace-to-metrics** correlation configured.
  - Trace context propagated end-to-end through **Kafka headers** (W3C traceparent): one trace must span HTTP ingest → Kafka publish → aggregator → rating → ledger posting. Add an integration test that asserts a single traceId appears in all services' logs for one event.
  - `tenant.id` as a span attribute everywhere (bounded cardinality with 3 demo tenants) so traces can be filtered per tenant in Tempo — this reinforces the multi-tenancy observability story.
  - Prometheus **exemplars** enabled on latency histograms so Grafana latency panels link directly to example traces.
  - Tempo **metrics-generator** (or collector spanmetrics connector) producing RED metrics from spans; add a "Trace-derived latency" Grafana panel and note in docs how span metrics compare to Micrometer's.
  - Benchmark note: run one benchmark pass with tracing at 100% sampling and one with tracing disabled; report the overhead delta for each gateway in BENCHMARK.md (tracing cost differs between reactor context propagation and thread-local propagation on virtual threads — call this out explicitly).
  - The `make demo` output should print a deep link to one full pipeline trace in local Grafana as its finale.

## 14a. Security Architecture — Dual Auth Model (docs/SECURITY.md)

**Deliberate design decision (record as an ADR):** two authentication mechanisms, chosen per traffic profile.

**1. Ingestion path — API keys (unchanged):**
High-volume machine-to-machine telemetry authenticates with per-tenant API keys (`Authorization: Bearer qk_live_...`), validated in-process against hashed keys (Argon2id) with a Caffeine cache. Rationale to document: SDK simplicity, no token refresh failure modes at 2k RPS, no dependency on an IdP in the hot path, and this matches how Stripe/OpenAI/Datadog authenticate ingestion. Key rotation supported: a tenant may have 2 active keys.

**2. Query/admin path — OAuth2 + OIDC via Keycloak:**
`ledger-service` REST APIs (balances, transactions, invoices, tenant admin) are Spring Security **OAuth2 Resource Servers** validating Keycloak-issued JWTs (local signature verification via JWKS, cached — no per-request IdP call).

- Keycloak realm `quotient`, fully provisioned as code (realm export JSON imported at container startup; seeded users/clients per demo tenant).
- **Tenant mapping:** single realm with a `tenant_id` claim (protocol mapper from a user/client attribute). Write up the realm-per-tenant alternative in docs/SECURITY.md as a decision matrix — deliberately mirroring the pool/bridge/silo database analysis in MULTI_TENANCY.md (same trade-off shape: isolation vs operational cost).
- **Roles:** `tenant-admin` (invoices, keys), `tenant-viewer` (read-only), `platform-operator` (cross-tenant, used by demo script only). Method security via `@PreAuthorize`.
- **Critical invariant:** the `tenant_id` JWT claim is the *only* source of tenant context on this path; it feeds the same `TenantContext` → Postgres `SET LOCAL app.tenant_id` chain as the API-key path. Add a test proving a valid token for tenant A cannot read tenant B's ledger even when passing `tenantId=B` in the URL/query (authorization derived from token, never from request parameters).
- **Flows:** `client_credentials` for service-to-service demo calls; authorization code flow documented with a curl/httpie walkthrough for humans.
- Token validation spans appear in Tempo traces; auth failures (401/403) are per-tenant-tagged metrics on the Grafana dashboards.
- `make demo` acquires a token from Keycloak via client_credentials before querying invoices — proving the full loop works headlessly.

docs/SECURITY.md must include a Mermaid diagram showing both auth paths converging on the shared tenant-context propagation chain, plus the threat-model table (spoofed tenant, replayed event, stolen key, cross-tenant query attempt → mitigation for each).



- Java 25 (Temurin), Gradle 8.x with version catalog, Spring Boot latest 3.x/4.x stable.
- **Gradle discipline:** keep the build deliberately boring and idiomatic. Kotlin DSL, one small convention plugin in `buildSrc` (or `build-logic`) for shared Java/test config — nothing else. No custom task classes, no clever scripting, no configuration-time logic. Every build file should be readable by a Maven developer in under a minute. Comment the convention plugin explaining what it replaces (a Maven parent POM). Record the Gradle-vs-Maven decision as an ADR (key points: incremental build + build cache + parallel module execution for a 7-module repo with a tight agent-driven edit-test loop; trade-off acknowledged: Maven is more universally readable).
- Unit tests (JUnit 5, AssertJ), integration tests with **Testcontainers** (Kafka, Postgres, Redis, Keycloak via dasniko testcontainers-keycloak), contract tests shared across both gateways, jqwik property tests for rating and ledger, ArchUnit rules (no double for money, no blocking in reactive module, module dependency rules), BlockHound in reactive tests.
- Coverage — **JaCoCo**, wired properly:
  - JaCoCo plugin applied via the convention plugin to every module; separate coverage collection for unit tests (`test`) and integration tests (`integrationTest` source set with Testcontainers), plus a **merged aggregate report** at the root (`jacoco-report-aggregation` plugin) so there is one HTML/XML report for the whole repo.
  - `jacocoTestCoverageVerification` gates enforced in CI: **85% line / 75% branch** on `rating-engine` and `ledger-service` business logic, **70% line** elsewhere. Exclusions must be principled and listed in one place (generated code, Spring `@Configuration` wiring, records/DTOs, main() classes) — never exclude domain logic to pass the gate.
  - Coverage badge in the README generated from the aggregate XML; `make coverage` opens the HTML report locally.
  - Rule for Claude Code: coverage is a floor, not a target — tests must assert behavior (given/when/then, meaningful failure messages), never exist to inflate line counts. A test without a meaningful assertion is a defect.
- **Clean code & SOLID conventions (enforced, not aspirational):**
  - Small, intention-revealing classes and methods; domain language from Section 5 used consistently (a `Charge` is never called a "billing item" in one module and a "fee" in another).
  - SRP: each service class has one reason to change — e.g., `RatingService` computes charges, `PlanRepository` loads plans, `ChargePublisher` emits events; no god classes, no `*Manager`/`*Util` dumping grounds.
  - DIP: modules depend on interfaces defined by the domain (ports), with Kafka/Postgres/Redis adapters at the edges — a light hexagonal shape, verified by ArchUnit rules (domain packages must not import Spring/Kafka/JDBC types).
  - OCP in practice: pricing models (flat/tiered/volume) implemented as a sealed interface + implementations, so adding a pricing model touches no existing rating code — call this out in docs as the worked SOLID example.
  - Constructor injection only (no field `@Autowired`); immutability by default (records, `List.copyOf`); no `null` returns in domain APIs — use `Optional` or throw.
  - Checkstyle (Google style, tuned) + Spotless + Error Prone all green in `make lint`; cyclomatic complexity cap 10 per method via Checkstyle.
  - Javadoc required on every public type in `quotient-common` and on all domain services; comments explain *why*, never *what*.
- Spotless + Error Prone. `make lint` clean.
- GitHub Actions: build, unit + Testcontainers integration tests, ledger-verify, docker build. (Benchmarks are local-only; CI just smoke-runs k6 for 30 s.)
- README: badges, 90-second quickstart, architecture diagram, screenshots/GIF of Grafana + demo output, links to all docs, "Design highlights" section.

## 16. Delivery Phases (Claude Code: implement in this order, commit per logical step, conventional commits)

1. **Scaffold:** repo layout, Gradle multi-module, version catalog, CLAUDE.md, CI skeleton, docker-compose infra only, Makefile. ✅ `make up` starts infra.
2. **Domain + common:** records, Money type, event schemas (JSON + versioned), tenant context. ✅ unit tests pass.
3. **Ledger-service core:** Flyway schema, RLS, double-entry posting, invariants, query API, ledger-verify. ✅ integration tests incl. cross-tenant isolation test.
4. **Ingestion (virtual threads first):** contract, validation, idempotency, rate limiting, Kafka publish. ✅ contract tests green.
5. **Meter-aggregator:** Kafka Streams EOS, windows, late events. ✅ Testcontainers topology tests (TopologyTestDriver + full IT).
6. **Rating-engine:** pricing models, determinism, plan versioning. ✅ property tests.
7. **Wire the pipeline + invoicing + outbox.** ✅ `make demo` passes end-to-end.
8. **Reactive gateway:** second implementation against shared contract tests. ✅ BlockHound clean.
9. **Observability:** dashboards, OTel tracing → Tempo (end-to-end trace across the pipeline, exemplars, trace-to-logs), JFR configs. ✅ single-trace-across-pipeline integration test green; demo prints a Grafana trace link.
9a. **Security hardening:** Keycloak realm-as-code, OAuth2 resource server on ledger-service, roles + method security, tenant-claim → TenantContext wiring, cross-tenant authorization tests, docs/SECURITY.md with threat model. ✅ token-for-tenant-A-cannot-read-tenant-B test green; `make demo` uses a real token.
10. **Benchmarks:** k6 scenarios, harness, report generation, run and record real numbers. ✅ BENCHMARK.md complete with real local results.
11. **Documentation pass:** all docs/, all Mermaid diagrams, ADRs (minimum: language/runtime, build tool — Gradle vs Maven, Kafka vs alternatives, RLS pool model, exactly-once strategy, minor-units money, outbox, dual-auth model — API keys on ingest vs OAuth2 on query, single-realm-with-claim vs realm-per-tenant, WebFlux vs Loom outcome), polish README.
12. **Final review:** dead code sweep, TODO sweep, fresh-clone test (`git clone && make up && make demo` on a clean machine).

## 17. CLAUDE.md (create at repo root with this content, adjusted as the project evolves)

- Project intent, module map, key invariants (money in minor units; ledger append-only; tenant context mandatory).
- Commands: build/test/itest/demo/bench.
- Conventions: package root `io.github.marciomarinho.quotient`, conventional commits, ADR-before-big-decision, constructor injection only, ports-and-adapters shape (domain never imports framework types), sealed interfaces for closed hierarchies, tests are given/when/then with behavioral assertions.
- "Never do": floating point money, cross-tenant queries without RLS context, blocking calls in reactive module, unbounded in-memory buffering, field injection, `*Util`/`*Manager` classes, excluding domain code from JaCoCo to pass a gate, tests without meaningful assertions.

## 18. Stretch Goals (only after Phase 12)

- Spend caps: real-time budget alerts via aggregator side-output + a webhook simulator.
- Credit grants / prepaid balance drawdown in the ledger.
- Schema registry (Apicurio) + Avro instead of JSON.
- A tiny static HTML "tenant portal" served by ledger-service showing live usage + invoices.
- jHiccup/async-profiler flamegraphs in the benchmark appendix.
