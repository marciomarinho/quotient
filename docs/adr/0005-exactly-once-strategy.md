# ADR-0005: Idempotency + Kafka EOS for correctness

- Status: accepted
- Date: 2026-07-08

## Context

Billing errors destroy trust: events must never be double-counted or lost, across
a multi-hop pipeline with at-least-once delivery. See docs/EXACTLY_ONCE.md.

## Decision

Layer three complementary guarantees rather than rely on one:

1. **Ingestion idempotency** — client `idempotencyKey`, deduped in Redis
   (`SET NX EX 86400`) before publish; dedup mark released if the publish fails.
2. **Aggregation exactly-once** — Kafka Streams `processing.guarantee=exactly_once_v2`;
   windows with grace + `suppress(untilWindowCloses)` emit one final reading.
3. **Deterministic charges + idempotent posting** — a charge id is a name-based
   UUID of its billing coordinates; the ledger posts with `ON CONFLICT DO NOTHING`,
   so replays never double-post.

## Consequences

- Any single layer failing does not cause double-billing; the ledger is the final
  backstop (idempotent + balanced by DB trigger).
- Trade-off: EOS v2 adds transactional overhead and commit latency; the Redis
  dedup window trades storage for a bounded replay-safety horizon (24h). `suppress`
  needs stream-time to advance to flush a window (documented; the demo uses a
  heartbeat).
