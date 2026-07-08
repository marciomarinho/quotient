# Quotient

**Token-level usage metering, rating, and ledger-grade billing — built for scale, runnable on a laptop.**

Quotient is a portfolio-grade demonstration of production monetization
infrastructure. It shows, end-to-end, how a modern usage-based billing platform
works: ingest token telemetry over HTTP, stream it through Kafka with per-tenant
partitioning and exactly-once processing, aggregate into billable meter
readings, rate against per-tenant pricing plans, and record every monetary
movement in a double-entry ledger with strict integrity invariants — all with
multi-tenant isolation at the API, Kafka, and database (PostgreSQL RLS) layers.

It also contains a rigorous, reproducible benchmark of two interchangeable
ingestion gateways: **Spring WebFlux** (reactive) vs **Spring MVC + virtual
threads** (Java 25, Project Loom).

Everything runs locally with `make up` — no cloud dependencies.

> **Status:** under active construction. Phase 1 (scaffold + infrastructure) is
> in place; see `quotient-project-plan.md` §16 for the delivery roadmap.

## Architecture

```
usage events (HTTP) ─▶ ingest-gateway ─▶ Kafka ─▶ meter-aggregator ─▶ rating-engine ─▶ ledger-service
  (token telemetry)     reactive |            usage.events.v1   (Kafka Streams)   (pricing)   (double-entry
                        vthreads               key=tenantId      EOS + windows                  ledger + RLS
                                                                                                + invoicing)
```

Full C4 + sequence diagrams: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) *(Phase 11)*.

## Quickstart (90 seconds)

Requires **JDK 25** (Temurin or GraalVM), **Docker**, and **k6**.

```bash
make up        # start Kafka, Postgres, Redis, Prometheus, Grafana, Tempo, Keycloak, Toxiproxy
make build     # compile all modules (Gradle 9.6.1 via ./gradlew, JDK 25 toolchain)
make test      # unit tests
# make demo    # (Phase 7+) fire realistic traffic across 3 tenants and print invoices + ledger balances
```

### Local endpoints

| Service | URL | Notes |
|---|---|---|
| Kafka (host) | `localhost:29092` | in-network: `kafka:9092` |
| Kafka UI | http://localhost:8085 | topic/lag visibility |
| Postgres | `localhost:5432` | `quotient` / `quotient` |
| Redis | `localhost:6379` | idempotency dedup |
| Prometheus | http://localhost:9090 | exemplars enabled |
| Grafana | http://localhost:3001 | `admin` / `admin` |
| Tempo | http://localhost:3200 | traces |
| Keycloak | http://localhost:8081 | `admin` / `admin`, realm `quotient` |
| Toxiproxy | http://localhost:8474 | benchmark fault injection |

## Repository layout

```
quotient-common/          domain records, Money type, event schemas, tenant context
ingest-gateway-reactive/  WebFlux + reactor-kafka gateway
ingest-gateway-vthreads/  MVC + virtual threads gateway
meter-aggregator/         Kafka Streams aggregator (EOS v2)
rating-engine/            pricing + charge production
ledger-service/           double-entry ledger + invoicing + query API (Postgres, RLS)
loadtest/                 k6 scenarios + benchmark report generator
build-logic/              the single Gradle convention plugin ("parent POM")
infra/                    provisioned-as-code configs (Prometheus, Tempo, OTel, Grafana, Keycloak)
docs/                     PROBLEM, ARCHITECTURE, MULTI_TENANCY, SECURITY, LEDGER_DESIGN, EXACTLY_ONCE, BENCHMARK, adr/
```

## Design highlights

- **Correctness under concurrency:** idempotent ingestion, exactly-once
  aggregation, and ledger invariants enforced at three layers (DB trigger, app
  service, consumer idempotency).
- **Money is exact:** `long` minor units end-to-end; no floating point (ArchUnit-enforced).
- **Multi-tenancy done properly:** API key / JWT claim → tenant context → Kafka
  partitioning → PostgreSQL Row-Level Security.
- **Honest performance engineering:** reproducible k6 benchmark of reactive vs
  virtual-threads gateways with real local numbers.

Docs, ADRs, dashboards, and benchmark results are added through the delivery
phases. License: Apache-2.0.
