# ADR-0012: GraalVM native images, starting with the rating-engine

- Status: accepted (rating-engine); proposed (remaining services)
- Date: 2026-07-08

## Context

Every service is a Spring Boot app on the JVM. The JVM is the right default —
JIT peak throughput, mature tooling, and blocking libraries all just work — but
it carries two costs that matter for a fleet of small, horizontally-scaled
consumers:

- **Startup latency.** A JVM Spring Boot context takes ~1.5–2.5 s to reach
  "Started". For a service that is scaled to zero and woken on demand, or
  rescheduled often (spot nodes, rolling deploys, autoscaling), that is dead time
  on every cold start.
- **Baseline memory.** The JVM reserves heap, metaspace, code cache and JIT
  structures before doing any work — a floor of a few hundred MB per replica.

[GraalVM native-image](https://www.graalvm.org/latest/reference-manual/native-image/)
does whole-program **ahead-of-time** compilation: it produces a standalone
executable with the reachable application + library code and a minimal runtime
(the SubstrateVM). Spring Boot's **AOT** engine cooperates by turning reflective
bean wiring into explicit code at build time, so the closed-world assumption
native-image requires actually holds.

The catch is the closed world: native-image must see, at build time, every class
reached by reflection, every resource, every JNI call. Anything discovered only
at runtime must be declared as **reachability metadata**. That is the whole game.

## Decision

**Adopt GraalVM native-image, service by service, starting with the
rating-engine** — the service where it is both easiest and most valuable.

The rating-engine is a pure Kafka consumer/producer: no web server, no database
driver, no JNI. Its only reflection surface is Jackson (de)serializing our own
event records. That makes it the ideal first target:

- Framework metadata (kafka-clients, Jackson, Spring, Micrometer) ships in the
  GraalVM **reachability-metadata repository**, enabled by default by the
  `org.graalvm.buildtools.native` Gradle plugin — no hand-written config.
- Our own records need one annotation. `RatingConfiguration` carries
  `@RegisterReflectionForBinding({MeterReading, Charge, BillingWindow, Money,
  Currency, TenantId, Aggregation})` so Jackson can bind them in the native image.

Build and run:

```bash
make native-rating          # ./gradlew :rating-engine:nativeCompile
./rating-engine/build/native/nativeCompile/rating-engine
```

The JVM build is untouched: `make build` still produces the executable bootJar,
and the Dockerfiles still run it on the JVM. Native is **additive** — a second
way to package the same service, chosen per deployment.

### One build-graph consequence worth recording

The native build resolves a service's own classes from its **plain library jar**,
not from the `build/classes` directory. We had previously disabled the plain
`jar` task (keeping only the bootJar) so `Dockerfile`s could `COPY build/libs/*.jar`
unambiguously. That broke `nativeCompile` — the app's main class dropped off the
native classpath. Fix: keep the plain `jar` enabled (Boot gives it the `-plain`
classifier) and narrow the Docker glob to `build/libs/*-SNAPSHOT.jar`, which
matches only the executable bootJar. See `quotient.java-conventions.gradle.kts`.

## Consequences

- **Startup collapses from seconds to milliseconds.** The native rating-engine
  reports `Started RatingEngineApplication in 0.049 seconds` — ~30–50× faster to
  first work than the JVM build. Cold-start and scale-to-zero become cheap.
- **Lower, flatter memory footprint** — no JIT/metaspace floor; RSS tracks actual
  working set. Good for dense, many-replica deployments.
- **Build cost moves to compile time.** `nativeCompile` takes ~1.5–2 min and
  needs several GB of RAM — far heavier than `javac`. It belongs in a release
  pipeline, not the inner dev loop. Day-to-day development stays on the JVM.
- **No JIT peak throughput.** AOT code does not get hotter under load. For these
  IO-bound services that is a non-issue; for a CPU-bound hot path it would be a
  real trade-off. Profile-guided optimization (PGO) is the mitigation if ever
  needed, and is an Oracle GraalVM feature.
- **The closed world is a standing tax.** Add a reflective/resource/proxy usage
  and you must add metadata or the native image fails at runtime where the JVM
  would not. New event types (de)serialized by Jackson must join the
  `@RegisterReflectionForBinding` set. This is the main reason to adopt per
  service rather than all at once.

### Why not (yet) the other services

| Service | Native difficulty | Blocker |
|---|---|---|
| **rating-engine** | easy ✅ | none — done |
| **ledger-service** | moderate | JDBC + Flyway + PostgreSQL driver need metadata; feasible with the driver's shipped hints |
| **ingest-gateway-vthreads / -reactive** | hard | Argon2id via JNI (`argon2-jvm`) + Bucket4j; JNI config and native library bundling required |
| **meter-aggregator** | hard | Kafka Streams' RocksDB state store is a JNI native library — the least native-friendly component |

Each remaining service is a follow-up with its own metadata work; this ADR
commits only to the pattern and to the rating-engine as the proof.
