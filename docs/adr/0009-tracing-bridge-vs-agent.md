# ADR-0009: Micrometer Tracing bridge over the OTel Java agent

- Status: accepted
- Date: 2026-07-08

## Context

We want distributed tracing to Tempo. Two options: the **OpenTelemetry Java
agent** (zero-code auto-instrumentation of Kafka/JDBC/Lettuce, including Kafka
Streams internals) or **Micrometer Tracing bridged to OpenTelemetry** (in-process,
cleaner custom spans, no agent).

## Decision

Use the **Micrometer Tracing + OTel bridge**. A headline deliverable is the
WebFlux-vs-virtual-threads benchmark (ADR-0011), and we want that to measure the
application, not a bytecode agent's overhead.

## Consequences

- Clean, first-class spans and metrics; no agent to attach; no agent overhead in
  the benchmark. Traceparent propagates over Kafka headers on the Spring-Kafka hops.
- **Known limitation (documented in docs/OBSERVABILITY.md):** the bridge does not
  instrument the Kafka Streams aggregator, so a single unbroken trace across the
  whole pipeline (HTTP → publish → aggregate → rate → post) requires the OTel agent.
  This is an accepted, documented gap: run with the agent when you need the full
  end-to-end trace, at the cost of the overhead the benchmark isolates.
