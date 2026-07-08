// ledger-service: double-entry ledger + invoicing + tenant-scoped query API.
// PostgreSQL (Spring Data JDBC + jOOQ), Flyway migrations, RLS, OAuth2 resource
// server (Phase 9a). The integrity centerpiece of the platform.

plugins {
    id("quotient.java-conventions")
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":quotient-common"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.data.jdbc)
    implementation(libs.spring.boot.starter.jooq)
    implementation(libs.spring.kafka)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.micrometer.registry.prometheus)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.kafka.test)
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.jqwik)

    "integrationTestImplementation"(libs.spring.boot.testcontainers)
    "integrationTestImplementation"(libs.testcontainers.junit)
    "integrationTestImplementation"(libs.testcontainers.postgresql)
    "integrationTestImplementation"(libs.testcontainers.kafka)
}
