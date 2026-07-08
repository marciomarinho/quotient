# Architecture

Quotient is a Kafka-backed pipeline: **ingest → aggregate → rate → post to a
double-entry ledger**, with multi-tenant isolation at every layer. This document
gives the C4 views and the key sequence flows. See [PROBLEM.md](PROBLEM.md) for
why, and the ADRs in [adr/](adr/) for the decisions.

## C4 — System Context

```mermaid
C4Context
    title System Context — Quotient
    Person(client, "API client / SDK", "Emits token-usage telemetry")
    Person(operator, "Operator / tenant admin", "Queries balances, generates invoices")
    System(quotient, "Quotient", "Usage metering, rating, ledger-grade billing")
    System_Ext(keycloak, "Keycloak", "OIDC identity provider")
    Rel(client, quotient, "Usage events", "HTTPS + API key")
    Rel(operator, quotient, "Ledger queries, invoicing", "HTTPS + JWT")
    Rel(quotient, keycloak, "Validates JWTs (JWKS)", "HTTPS")
    Rel(operator, keycloak, "Obtains token", "OAuth2")
```

## C4 — Containers

```mermaid
C4Container
    title Containers — Quotient
    Person(client, "API client")
    Person(operator, "Operator")

    System_Boundary(q, "Quotient") {
        Container(gateway, "ingest-gateway", "Spring WebFlux | MVC + virtual threads", "Auth, validate, dedup, rate-limit, publish")
        ContainerQueue(kafka, "Kafka", "KRaft", "usage.events / usage.aggregates / billing.charges / invoice.created")
        Container(aggregator, "meter-aggregator", "Kafka Streams", "Tumbling windows, exactly-once v2")
        Container(rating, "rating-engine", "Spring Kafka", "Tiered/volume/flat pricing -> charges")
        Container(ledger, "ledger-service", "Spring + jOOQ/JDBC", "Double-entry ledger, invoices, query API")
        ContainerDb(postgres, "PostgreSQL 17", "Row-Level Security", "Ledger, invoices, outbox")
        ContainerDb(redis, "Redis 7", "", "Idempotency dedup")
    }
    System_Ext(keycloak, "Keycloak", "OIDC")

    Rel(client, gateway, "POST /v1/usage/events", "HTTPS")
    Rel(operator, ledger, "GET balances / POST invoices", "HTTPS + JWT")
    Rel(gateway, redis, "SET NX EX", "RESP")
    Rel(gateway, kafka, "usage.events.v1 (key=tenantId)", "")
    Rel(kafka, aggregator, "consume usage.events.v1", "")
    Rel(aggregator, kafka, "usage.aggregates.v1", "")
    Rel(kafka, rating, "consume usage.aggregates.v1", "")
    Rel(rating, kafka, "billing.charges.v1", "")
    Rel(kafka, ledger, "consume billing.charges.v1", "")
    Rel(ledger, postgres, "post / query (SET LOCAL app.tenant_id)", "JDBC")
    Rel(ledger, keycloak, "JWKS", "HTTPS")
```

## C4 — Component (ledger-service)

```mermaid
flowchart TB
    subgraph Ledger["ledger-service"]
        CL[ChargeListener<br/>Kafka consumer]
        PS[LedgerPostingService<br/>retry-on-40001]
        TW[TransactionalLedgerWriter<br/>SERIALIZABLE]
        DEP[DoubleEntryPosting<br/>domain]
        REPO[JdbcLedgerRepository<br/>RLS-bound]
        INV[InvoiceService]
        IREPO[JdbcInvoiceRepository]
        RELAY[OutboxRelay<br/>@Scheduled]
        QC[LedgerQueryController<br/>OAuth2 + @PreAuthorize]
        SEC[JwtTenantContextFilter<br/>tenant_id claim -> TenantContext]
    end
    KAFKA[(Kafka<br/>billing.charges.v1)] --> CL --> PS --> TW
    DEP --> PS
    TW --> REPO --> PG[(PostgreSQL + RLS)]
    SEC --> QC --> QSVC[LedgerQueryService] --> REPO
    INV --> IREPO --> PG
    RELAY --> PG
    RELAY --> OUT[(Kafka<br/>invoice.created.v1)]
```

## Sequence — event ingestion (happy path)

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant G as ingest-gateway
    participant R as Redis
    participant K as Kafka
    C->>G: POST /v1/usage/events (Bearer qk_live_...)
    G->>G: verify API key (Argon2id, cached) -> TenantId
    G->>G: validate (meter exists, occurredAt <= now+5m)
    G->>R: SET idem:<tenant>:<key> NX EX 86400
    R-->>G: OK (first time)
    G->>K: publish usage.events.v1 (key=tenantId, acks=all)
    K-->>G: ack
    G-->>C: 202 Accepted {deduplicated:false}
```

## Sequence — duplicate event rejection

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant G as ingest-gateway
    participant R as Redis
    participant K as Kafka
    C->>G: POST /v1/usage/events (same idempotencyKey)
    G->>R: SET idem:<tenant>:<key> NX EX 86400
    R-->>G: nil (key already present)
    Note over G,K: no publish — event already seen
    G-->>C: 202 Accepted {deduplicated:true}
```

## Sequence — window close → rating → ledger posting

```mermaid
sequenceDiagram
    autonumber
    participant A as meter-aggregator
    participant K as Kafka
    participant RT as rating-engine
    participant L as ledger-service
    participant P as PostgreSQL
    A->>A: window [t, t+1m) closes (grace 30s), suppress emits
    A->>K: usage.aggregates.v1 (MeterReading)
    K->>RT: consume MeterReading
    RT->>RT: apply tenant plan (tiered/volume/flat), record planVersion
    RT->>K: billing.charges.v1 (Charge, deterministic id)
    K->>L: consume Charge
    L->>P: SET LOCAL app.tenant_id; INSERT transaction ON CONFLICT DO NOTHING
    L->>P: INSERT balanced entries (DEBIT RECEIVABLE / CREDIT REVENUE + TAX)
    Note over P: deferred trigger asserts debits == credits at COMMIT
    L->>P: record billed_charge (for invoicing)
```

For invoice generation and the outbox flow, see [LEDGER_DESIGN.md](LEDGER_DESIGN.md).
