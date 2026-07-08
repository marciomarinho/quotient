# ADR-0006: Money as `long` minor units, never floating point

- Status: accepted
- Date: 2026-07-08

## Context

Monetary math must be exact. Floating point (`double`/`float`) silently loses
precision and is unacceptable for billing.

## Decision

Represent all money as a **`long` count of minor units** (cents) plus a
`Currency` (AUD), in one `Money` value type. Arithmetic is exact and overflows
loudly (`Math.addExact`/`multiplyExact`). `BigDecimal` is permitted **only** at
the unit-price × quantity step (rating) and the GST percentage, always with an
explicit `HALF_EVEN` rounding mode, immediately converted back to minor units.

An **ArchUnit test** forbids `double`/`float` fields in the domain.

## Consequences

- No floating-point drift anywhere in the money path; overflow surfaces as an
  exception rather than a wrong balance.
- The single `Money` type keeps the domain language consistent (a `Charge` is
  never a "fee" elsewhere).
- Trade-off: callers must think in minor units and choose rounding explicitly at
  the two permitted spots — which is the point.
