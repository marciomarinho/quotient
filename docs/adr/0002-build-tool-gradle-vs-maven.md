# ADR-0002: Gradle (Kotlin DSL) over Maven

- Status: accepted
- Date: 2026-07-08

## Context

A 6-module monorepo with a tight agent-driven edit-test loop. We want fast
incremental builds and a boring, readable build.

## Decision

Use **Gradle 9 with the Kotlin DSL** and a **version catalog**
(`gradle/libs.versions.toml`). Keep exactly one shared **convention plugin**
(`build-logic/`) that stands in for a Maven parent POM (Java toolchain, JUnit +
`integrationTest` source set, JaCoCo, Spotless, Checkstyle). No custom task
classes, no configuration-time scripting.

## Consequences

- Incremental build + build cache + parallel module execution make the edit-test
  loop fast; the single convention plugin keeps the build understandable in a
  minute (a Maven dev can read it).
- Trade-off acknowledged: **Maven is more universally readable** and its XML is
  more uniform. We accept a slightly steeper "what does this Kotlin do" cost for
  speed and DRY.
- Constraint discovered: Gradle must run on a JDK that supports Java 25 (Gradle
  9.6+); precompiled script plugins can't use the type-safe `libs` accessor, so
  the convention plugin hardcodes a few build-tool versions (documented inline).
