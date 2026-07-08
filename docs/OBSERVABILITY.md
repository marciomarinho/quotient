# Observability

Quotient is instrumented for the three pillars — metrics, logs, and traces —
all provisioned as code and viewable in the local Grafana.

## Metrics (Micrometer → Prometheus)

Every service exposes `/actuator/prometheus`. Prometheus scrapes them (see
`infra/prometheus/prometheus.yml`). On top of the standard RED metrics
(`http_server_requests_seconds*`, JVM, Kafka client metrics) we emit two
**per-tenant** counters with deliberately bounded cardinality (the demo has three
tenants):

| Metric | Tags | Meaning |
|---|---|---|
| `quotient_ingest_events_total` | `tenant`, `outcome` (`accepted`/`deduplicated`) | ingestion outcomes per tenant |
| `quotient_ledger_postings_total` | `tenant`, `outcome` (`posted`/`duplicate`) | ledger postings per tenant |

Three Grafana dashboards are provisioned (`infra/grafana/dashboards/`):
**Ingestion** (RPS, latency percentiles, accepted-vs-deduplicated, 429s),
**Pipeline** (consumer lag, records/s, rating throughput, trace-derived latency),
and **Ledger** (postings/s, totals, latency).

Latency histograms enable **exemplars** (`percentiles-histogram` +
Prometheus `exemplar-storage`), so a latency bucket in Grafana links to an
example trace.

## Logs (structured, ECS JSON)

All services log structured **ECS JSON** to the console
(`logging.structured.format.console=ecs`), so logs carry `@timestamp`,
`log.level`, `service.name`, and — for anything logged inside a request/consume
span — `trace.id` and `span.id` (added to MDC by Micrometer Tracing). The
gateways also put `tenant.id` in the MDC, so a tenant's log lines are filterable.

## Traces (Micrometer Tracing → OTLP → Tempo)

We use **Micrometer Tracing bridged to OpenTelemetry**, exporting OTLP to the
`otel-collector`, which forwards to **Tempo**. Grafana's Tempo datasource is
provisioned with trace-to-metrics and service-graph correlation, and Tempo's
metrics-generator produces span RED metrics remote-written to Prometheus.

`make demo` finishes by printing a deep link to one pipeline trace in Tempo.

### Design choice & a known limitation (see ADR-0009)

The plan offered two ways to trace: the **OTel Java agent** (zero-code spans for
Kafka/JDBC/Lettuce, including Kafka Streams internals) or the **Micrometer +
OTel bridge** (cleaner custom spans, no agent, no agent overhead in the
benchmark). We chose the **bridge** so the WebFlux-vs-virtual-threads benchmark
measures the application, not an agent.

The trade-off: the bridge propagates the W3C `traceparent` over Kafka headers on
the Spring-Kafka hops (producer/consumer observation is enabled), and each
service exports its spans to Tempo — but the **meter-aggregator is a Kafka
Streams application, which the bridge does not instrument**, so a trace does not
survive the Streams stage. A single unbroken trace spanning HTTP → publish →
aggregate → rate → post therefore requires the OTel agent (or bespoke Streams
instrumentation). This is a deliberate, documented gap, not an oversight: run the
services with the OTel agent to get the full end-to-end trace, at the cost of the
agent overhead the benchmark is trying to isolate.
