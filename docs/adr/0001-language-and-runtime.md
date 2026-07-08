# ADR-0001: Java 25 as the language and runtime

- Status: accepted
- Date: 2026-07-08

## Context

The platform is I/O-bound (HTTP, Kafka, Postgres, Redis) and correctness-critical
(money, ledger). We want modern language ergonomics and a concurrency model that
scales blocking code, without adopting a niche stack.

## Decision

Use **Java 25 (LTS)** across all services, on Spring Boot 3.5.

Leverage current language/runtime features: **virtual threads** (Project Loom)
for one of the two ingestion gateways; **records** for immutable domain/event
types; **sealed interfaces** for closed hierarchies (pricing models); **pattern
matching**; and exact integer arithmetic (`Math.addExact`/`multiplyExact`) for money.

## Consequences

- The virtual-threads gateway can keep straightforward blocking code that still
  scales — directly comparable against the reactive gateway (see ADR-0011).
- Records + sealed types make the domain compact and the compiler an ally
  (exhaustive `switch`, unrepresentable illegal states like an unbalanced posting).
- Cost: Java 25 is new — some tooling lags (BlockHound cannot yet instrument
  JDK 25; Gradle must run on a JDK-25-capable version). These are documented where
  they bite.
