# ADR-0007: Transactional outbox for `invoice.created`

- Status: accepted
- Date: 2026-07-08

## Context

Generating an invoice writes rows to Postgres AND must emit an `invoice.created`
Kafka event. A naive "write DB then publish" can lose the event (crash between the
two) or publish a phantom event (publish then DB rollback) — the dual-write problem.

## Decision

Use the **transactional outbox** pattern. Invoice generation writes the invoice,
its lines, the charge links, **and an `outbox` row** in one SERIALIZABLE
transaction. A scheduled **relay** polls unpublished outbox rows, publishes them to
`invoice.created.v1`, and stamps `published_at`.

## Consequences

- The event can never be lost or phantom relative to the invoice: they commit
  atomically; the relay guarantees eventual, at-least-once delivery (consumers are
  idempotent).
- The `outbox` table is internal infrastructure and is de-RLS'd so the single relay
  can drain every tenant's rows.
- Trade-off: at-least-once (a row may publish twice if the relay crashes after
  send, before stamping) — acceptable because downstream is idempotent; plus the
  small latency of the poll interval.
