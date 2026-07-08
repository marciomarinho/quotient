// ingest-gateway-vthreads: Spring MVC + virtual threads (Project Loom)
// implementation of the ingestion contract. Blocking KafkaTemplate on virtual
// threads. Compared against the reactive gateway in docs/BENCHMARK.md.

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
    implementation(libs.spring.kafka)
    implementation(libs.spring.data.redis)
    implementation(libs.bucket4j.core)
    implementation(libs.argon2)
    implementation(libs.caffeine)
    implementation(libs.springdoc.webmvc)
    implementation(libs.micrometer.registry.prometheus)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.kafka.test)
    testImplementation(libs.rest.assured)

    "integrationTestImplementation"(libs.spring.boot.testcontainers)
    "integrationTestImplementation"(libs.testcontainers.junit)
    "integrationTestImplementation"(libs.testcontainers.kafka)
    "integrationTestImplementation"(libs.testcontainers.redis)
}
