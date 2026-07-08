# Architecture

Quotient is a Kafka-backed pipeline: **ingest → aggregate → rate → post to a
double-entry ledger**, with multi-tenant isolation at every layer. This document
gives the C4 views and the key sequence flows. See [PROBLEM.md](PROBLEM.md) for
why, and the ADRs in [adr/](adr/) for the decisions.

## C4 — System Context

```mermaid
flowchart TB
    client(["API client or SDK<br/>emits usage telemetry"]):::person
    operator(["Operator or tenant admin<br/>queries and invoicing"]):::person
    quotient["Quotient<br/>usage metering, rating, ledger billing"]:::sys
    keycloak[("Keycloak<br/>OIDC identity provider")]:::ext

    client -->|"usage events, HTTPS and API key"| quotient
    operator -->|"ledger queries, HTTPS and JWT"| quotient
    operator -->|"obtain token, OAuth2"| keycloak
    quotient -->|"validate JWT via JWKS"| keycloak

    classDef person fill:#dbe5f3,stroke:#33517a,color:#12212f;
    classDef sys fill:#1168bd,stroke:#0b4884,color:#ffffff;
    classDef ext fill:#e6e6e6,stroke:#7a7a7a,color:#222;
```

## C4 — Containers

Arrows between services are Kafka topics (versioned, keyed by `tenantId`);
dotted arrows are datastore access.

```mermaid
flowchart TB
    client(["API client"]):::person
    operator(["Operator"]):::person
    gw["ingest-gateway<br/>Spring WebFlux or MVC + virtual threads"]:::svc
    agg["meter-aggregator<br/>Kafka Streams, exactly-once v2"]:::svc
    rate["rating-engine<br/>Spring Kafka"]:::svc
    ledger["ledger-service<br/>Spring + JDBC and jOOQ"]:::svc
    redis[("Redis 7<br/>idempotency dedup")]:::db
    pg[("PostgreSQL 17<br/>Row-Level Security")]:::db
    kc[("Keycloak<br/>OIDC")]:::ext

    client -->|"POST usage events, HTTPS"| gw
    gw -->|"usage.events.v1"| agg
    agg -->|"usage.aggregates.v1"| rate
    rate -->|"billing.charges.v1"| ledger
    operator -->|"queries and invoices, JWT"| ledger
    gw -.->|"SET NX"| redis
    ledger -.->|"post and query, SET LOCAL app.tenant_id"| pg
    ledger -.->|"JWKS"| kc

    classDef person fill:#dbe5f3,stroke:#33517a,color:#12212f;
    classDef svc fill:#e4efe0,stroke:#3f7a4e,color:#16301f;
    classDef db fill:#f4ecd6,stroke:#9a7f3f,color:#3a2f1a;
    classDef ext fill:#e6e6e6,stroke:#7a7a7a,color:#222;
```

## C4 — Component (ledger-service)

```mermaid
flowchart TB
    subgraph Ledger["ledger-service"]
        CL["ChargeListener - Kafka consumer"]
        PS["LedgerPostingService - retry on 40001"]
        TW["TransactionalLedgerWriter - SERIALIZABLE"]
        DEP["DoubleEntryPosting - domain"]
        REPO["JdbcLedgerRepository - RLS-bound"]
        INV["InvoiceService"]
        IREPO["JdbcInvoiceRepository"]
        RELAY["OutboxRelay - scheduled"]
        QC["LedgerQueryController - OAuth2 and PreAuthorize"]
        QSVC["LedgerQueryService"]
        SEC["JwtTenantContextFilter - tenant_id claim into TenantContext"]
    end
    KAFKA[("Kafka - billing.charges.v1")] --> CL --> PS --> TW
    DEP --> PS
    TW --> REPO --> PG[("PostgreSQL - RLS")]
    SEC --> QC --> QSVC --> REPO
    INV --> IREPO --> PG
    RELAY --> PG
    RELAY --> OUT[("Kafka - invoice.created.v1")]
```

## Sequence — event ingestion (happy path)

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant G as ingest-gateway
    participant R as Redis
    participant K as Kafka
    C->>G: POST usage event with API key
    G->>G: verify API key with Argon2id, resolve TenantId
    G->>G: validate meter exists and timestamp not in the future
    G->>R: SET idempotency key NX EX 86400
    R-->>G: OK, first time seen
    G->>K: publish usage.events.v1, key is tenantId, acks all
    K-->>G: ack
    G-->>C: 202 Accepted, deduplicated false
```

## Sequence — duplicate event rejection

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant G as ingest-gateway
    participant R as Redis
    participant K as Kafka
    C->>G: POST usage event with same idempotency key
    G->>R: SET idempotency key NX EX 86400
    R-->>G: nil, key already present
    Note over G,K: no publish, event already seen
    G-->>C: 202 Accepted, deduplicated true
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
    A->>A: window closes after grace, suppress emits the final reading
    A->>K: publish usage.aggregates.v1 MeterReading
    K->>RT: consume MeterReading
    RT->>RT: apply tenant plan, record planVersion
    RT->>K: publish billing.charges.v1 Charge with deterministic id
    K->>L: consume Charge
    L->>P: SET LOCAL app.tenant_id, insert transaction ON CONFLICT DO NOTHING
    L->>P: insert balanced entries DEBIT RECEIVABLE, CREDIT REVENUE and TAX
    Note over P: deferred trigger asserts debits equal credits at COMMIT
    L->>P: record billed_charge for invoicing
```

For invoice generation and the outbox flow, see [LEDGER_DESIGN.md](LEDGER_DESIGN.md).
