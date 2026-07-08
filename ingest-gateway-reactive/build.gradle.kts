// ingest-gateway-reactive: Spring WebFlux + reactor-kafka implementation of the
// ingestion contract. No blocking on the event loop (enforced by BlockHound in
// tests). Externally identical to the vthreads gateway.

plugins {
    id("quotient.java-conventions")
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":quotient-common"))

    implementation(libs.spring.boot.starter.webflux)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.reactor.kafka)
    implementation(libs.spring.data.redis)
    implementation(libs.bucket4j.core)
    implementation(libs.argon2)
    implementation(libs.caffeine)
    implementation(libs.springdoc.webflux)
    implementation(libs.micrometer.registry.prometheus)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation("io.projectreactor:reactor-test")
    testImplementation(libs.blockhound)
    testImplementation(libs.rest.assured)

    "integrationTestImplementation"(libs.spring.boot.testcontainers)
    "integrationTestImplementation"(libs.testcontainers.junit)
    "integrationTestImplementation"(libs.testcontainers.kafka)
    "integrationTestImplementation"(libs.testcontainers.redis)
}
