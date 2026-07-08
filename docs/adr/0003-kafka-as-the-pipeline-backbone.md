# ADR-0003: Kafka as the streaming backbone

- Status: accepted
- Date: 2026-07-08

## Context

Usage events must be ingested cheaply, decoupled from downstream processing,
replayable, and partitioned per tenant. Alternatives considered: write straight
to Postgres (see docs/PROBLEM.md — collapses at scale), a queue like RabbitMQ/SQS
(weaker replay/stream-processing story), or Pulsar (fewer ecosystem tools).

## Decision

Use **Apache Kafka** (single-node KRaft locally) as the backbone, with per-tenant
partitioning (`key = tenantId`), and **Kafka Streams** for windowed aggregation.
Topics are versioned: `usage.events.v1`, `usage.aggregates.v1`,
`billing.charges.v1`, `invoice.created.v1`.

## Consequences

- Ingestion is decoupled from rating/ledger; events are durable and **replayable**
  (reprocess with new rating logic without touching ingestion).
- Per-tenant keys give ordering and downstream affinity; Kafka Streams provides
  windowing + **exactly-once v2** (see ADR-0005).
- Backpressure is explicit: if Kafka is unavailable the gateway returns 503, never
  buffering unbounded.
- Cost: operational weight of Kafka; Kafka Streams tracing needs the OTel agent
  (ADR-0009); local KRaft config has sharp edges (advertised listeners).
