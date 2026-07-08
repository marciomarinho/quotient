// Root build file. Intentionally minimal: shared configuration lives in the
// `quotient.java-conventions` convention plugin (build-logic/), which each
// module applies. The root only wires the aggregate JaCoCo report so there is
// one coverage HTML/XML for the whole repo.

plugins {
    id("jacoco-report-aggregation")
    base
}

dependencies {
    // Collect coverage from every application/library module into one report.
    jacocoAggregation(project(":quotient-common"))
    jacocoAggregation(project(":ingest-gateway-reactive"))
    jacocoAggregation(project(":ingest-gateway-vthreads"))
    jacocoAggregation(project(":meter-aggregator"))
    jacocoAggregation(project(":rating-engine"))
    jacocoAggregation(project(":ledger-service"))
}

reporting {
    reports {
        val testCodeCoverageReport by creating(JacocoCoverageReport::class) {
            testSuiteName = "test"
        }
    }
}

// `./gradlew coverage` -> one aggregate HTML report at
// build/reports/jacoco/testCodeCoverageReport/html/index.html
tasks.register("coverage") {
    group = "verification"
    description = "Builds the aggregate JaCoCo coverage report for all modules."
    dependsOn(tasks.named("testCodeCoverageReport"))
}
