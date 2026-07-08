# Benchmark: WebFlux vs Virtual Threads

The ingestion gateway is implemented twice against **one contract**:
`ingest-gateway-reactive` (Spring WebFlux, Netty event loop) and
`ingest-gateway-vthreads` (Spring MVC on Java 25 virtual threads). This is an
honest, reproducible comparison of the two concurrency models on the same
workload, not a marketing exercise.

## Methodology

- Both gateways run as containers with an **identical envelope** — 2 CPU / 1 GB
  (`deploy.resources.limits` in `docker-compose.yml`) — against the same Kafka
  and Redis.
- Load is generated with **k6** (`loadtest/scripts/single-event.js`) using the
  `constant-arrival-rate` executor, so we drive a fixed target RPS and measure
  how each gateway copes, rather than letting the client back off.
- Scenario: sustained single-event ingestion at **500 / 1000 / 2000 RPS** for a
  fixed duration. Each request authenticates with an API key, is deduped in
  Redis, and is published to Kafka with `acks=all` (the publish ack is on the
  request path).
- Reproduce with: `make up && docker compose --profile apps up -d --build && make bench`.

### What is measured

Throughput (achieved req/s), latency percentiles (p50/p95/p99/max), error rate
(k6 `http_req_failed`), and container CPU / RSS sampled at the end of each run
(`docker stats`). The numbers below are from a local run on the developer
machine — treat them as **relative** (same host, same limits), not absolute
SLA figures.

## Results

<!-- RESULTS:BEGIN -->

| Gateway | Target RPS | Throughput (req/s) | p50 (ms) | p95 (ms) | p99 (ms) | max (ms) | Errors | CPU | RSS |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| vthreads | 500 | 500 | 1.0 | 2.4 | 7.9 | 36.1 | 0.00% | 0.18% | 418.6MiB |
| vthreads | 1000 | 1000 | 0.7 | 1.3 | 4.5 | 36.6 | 0.00% | 0.12% | 440.7MiB |
| vthreads | 2000 | 1977 | 0.7 | 842.5 | 1825.3 | 2023.1 | 0.00% | 0.18% | 1003MiB |
| reactive | 500 | 500 | 1.0 | 2.0 | 8.4 | 68.7 | 0.00% | 0.20% | 695.4MiB |
| reactive | 1000 | 1000 | 0.7 | 1.3 | 5.0 | 16.2 | 0.00% | 0.15% | 394.4MiB |
| reactive | 2000 | 2000 | 0.7 | 4.2 | 12.3 | 34.8 | 0.00% | 0.10% | 685.9MiB |

<!-- RESULTS:END -->

## Analysis

Reading the local run (2 CPU / 1 GB each; the first run against each gateway is a
warmup and discarded):

- **Up to 1000 RPS the two are a wash.** Both sustain the target rate with a p99
  under ~8 ms and modest memory (~400 MB). At this load the choice is about code,
  not performance.
- **At 2000 RPS they diverge sharply.** The **reactive** gateway holds the target
  (2000 req/s, **p99 ≈ 12 ms**, RSS ≈ 686 MB). The **vthreads** gateway falls
  behind (≈1977 req/s, **p99 ≈ 1.8 s**, RSS climbing to **~1 GB — the container
  limit**). Under this envelope, ~2000 concurrent requests each parked on a
  blocking `acks=all` Kafka ack means ~2000 live virtual threads; their stacks and
  the in-flight state push memory to the cap and the tail latency balloons. The
  non-blocking reactive pipeline carries the same rate with no parked threads and
  roughly a third less memory.

So on *this* hardware and envelope: virtual threads are simpler and fully
competitive to ~1000 RPS, but the reactive gateway degrades more gracefully at
the top of the range and is markedly more memory-efficient there. Give a virtual-
thread service more memory and the gap narrows — which is itself the point:
**virtual threads trade memory for simplicity; reactive trades simplicity for a
leaner non-blocking envelope.**

Caveats (honest ones): single host shared with the load generator; short runs;
one machine; `acks=all` on the request path exaggerates the blocking cost (a
fire-and-forget publish would flatter vthreads). Treat the numbers as relative,
and re-run with `make bench` on your own hardware.

### Where the models genuinely diverge

The most interesting scenario is a **slow downstream**: inject ~50 ms of latency
on the Kafka publish (via Toxiproxy) so each request parks waiting for the ack.

- **Virtual threads (vthreads):** the blocking `KafkaTemplate.send().get()` parks
  the *virtual* thread; the carrier thread is released, so thousands of in-flight
  requests cost almost no platform threads. The code reads as straightforward
  blocking code, and stack traces are ordinary.
- **Reactive (WebFlux):** the same wait is a non-blocking `Mono` on the event
  loop; no thread is parked at all. Throughput under a slow downstream is
  excellent, but the code is a reactor chain (harder stack traces, BlockHound
  needed to keep the event loop clean, and libraries must be non-blocking).

Both handle the slow-downstream case far better than a classic thread-per-request
MVC gateway would — that is the whole point. The trade-off is **operational and
cognitive**, not raw throughput: virtual threads let you keep imperative code and
existing blocking libraries; WebFlux gives a fully non-blocking pipeline at the
cost of the reactive programming model and its debugging tax.

> Note on the Toxiproxy scenario: routing the gateway→Kafka hop through Toxiproxy
> is complicated by Kafka's advertised listeners (the broker redirects clients to
> `kafka:9092`), so the automated harness ships the sustained scenarios; the
> slow-downstream wiring is described here as the qualitative differentiator.
