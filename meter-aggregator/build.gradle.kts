// meter-aggregator: Kafka Streams application. Tumbling windows, exactly-once
// v2, late-event grace period. Consumes usage.events.v1, emits
// usage.aggregates.v1.

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
    implementation(libs.kafka.streams)
    implementation(libs.micrometer.registry.prometheus)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.kafka.test)
    testImplementation("org.apache.kafka:kafka-streams-test-utils")

    "integrationTestImplementation"(libs.spring.boot.testcontainers)
    "integrationTestImplementation"(libs.testcontainers.junit)
    "integrationTestImplementation"(libs.testcontainers.kafka)
}
