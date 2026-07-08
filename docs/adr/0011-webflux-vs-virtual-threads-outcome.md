# ADR-0011: Keep both gateways; when each concurrency model wins

- Status: accepted
- Date: 2026-07-08

## Context

The ingestion contract is implemented twice — Spring WebFlux (reactive) and
Spring MVC + Java 25 virtual threads — to compare the two concurrency models
honestly on the same workload. See docs/BENCHMARK.md for the numbers.

## Decision

**Keep both** as first-class, contract-equivalent implementations rather than pick
one. The comparison is the deliverable; the code documents when each model wins.

## Consequences (what the numbers and code show)

- **To ~1000 RPS they are a wash** (p99 < 8 ms). The choice is about code, not
  performance.
- **At 2000 RPS under a 2 CPU / 1 GB cap they diverge:** the reactive gateway holds
  p99 ≈ 12 ms at ~686 MB; the vthreads gateway degrades to p99 ≈ 1.8 s and hits the
  1 GB limit (~2000 virtual threads parked on the blocking `acks=all` publish).
- **Debuggability & ecosystem:** virtual threads keep imperative code and ordinary
  stack traces and reuse blocking libraries; WebFlux gives a leaner non-blocking
  envelope but costs the reactive model, harder stack traces, and needs BlockHound
  to keep the event loop clean.
- Verdict: **virtual threads trade memory for simplicity; reactive trades
  simplicity for a leaner envelope.** Prefer virtual threads unless you are memory-
  constrained at the very top of your load range.
