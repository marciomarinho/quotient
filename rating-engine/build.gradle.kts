// rating-engine: plain Spring Boot Kafka consumer/producer. Applies tiered /
// volume / flat pricing to aggregates and emits Charge records. Deterministic
// (property-tested with jqwik).

plugins {
    id("quotient.java-conventions")
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":quotient-common"))

    implementation(libs.spring.boot.starter)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.kafka)
    implementation(libs.micrometer.registry.prometheus)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.kafka.test)
    testImplementation(libs.jqwik)

    "integrationTestImplementation"(libs.spring.boot.testcontainers)
    "integrationTestImplementation"(libs.testcontainers.junit)
    "integrationTestImplementation"(libs.testcontainers.kafka)
}
