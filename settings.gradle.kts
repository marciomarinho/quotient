// Root settings for the Quotient multi-module build.
// Every application/library module is listed here explicitly — no dynamic
// discovery — so the module graph is obvious at a glance (Maven <modules>).

pluginManagement {
    // build-logic holds our single convention plugin (the "parent POM").
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "quotient"

include(
    "quotient-common",
    "ingest-gateway-reactive",
    "ingest-gateway-vthreads",
    "meter-aggregator",
    "rating-engine",
    "ledger-service",
)
