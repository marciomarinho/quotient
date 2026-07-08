// quotient-common: pure domain — records, Money type, event schemas, tenant
// context. Deliberately framework-free so it can be shared by every service
// and so ArchUnit can forbid Spring/Kafka/JDBC imports from the domain.

plugins {
    id("quotient.java-conventions")
    `java-library`
}

dependencies {
    // Bean Validation annotations only (no Spring). Jackson for schema binding.
    api("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2")
    api(libs.jakarta.validation)

    testImplementation(libs.archunit.junit5)
    testImplementation(libs.jqwik)
}
