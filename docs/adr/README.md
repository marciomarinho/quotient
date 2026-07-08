# Architecture Decision Records

Short records of the significant decisions in Quotient, in [MADR](https://adr.github.io/madr/)
style. Each captures the context, the decision, and the consequences (including
the trade-off accepted).

| # | Decision |
|---|---|
| [0001](0001-language-and-runtime.md) | Java 25 (virtual threads, records, pattern matching) |
| [0002](0002-build-tool-gradle-vs-maven.md) | Gradle (Kotlin DSL) over Maven |
| [0003](0003-kafka-as-the-pipeline-backbone.md) | Kafka as the streaming backbone |
| [0004](0004-multi-tenancy-rls-pool-model.md) | Pool multi-tenancy with PostgreSQL RLS |
| [0005](0005-exactly-once-strategy.md) | Idempotency + Kafka EOS for correctness |
| [0006](0006-money-as-minor-units.md) | Money as `long` minor units, never floating point |
| [0007](0007-transactional-outbox-for-invoicing.md) | Transactional outbox for `invoice.created` |
| [0008](0008-dual-auth-model.md) | Dual auth: API keys on ingest, OAuth2 on query |
| [0009](0009-tracing-bridge-vs-agent.md) | Micrometer Tracing bridge over the OTel agent |
| [0010](0010-single-realm-with-tenant-claim.md) | Single Keycloak realm + `tenant_id` claim |
| [0011](0011-webflux-vs-virtual-threads-outcome.md) | Keep both gateways; when each model wins |
| [0012](0012-graalvm-native-images.md) | GraalVM native images, starting with the rating-engine |
