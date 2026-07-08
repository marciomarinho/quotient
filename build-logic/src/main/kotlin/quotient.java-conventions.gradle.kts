// ===========================================================================
// quotient.java-conventions
// ---------------------------------------------------------------------------
// The single convention plugin for this repo. It is deliberately the ONLY
// piece of shared build logic and exists to replace what a Maven parent POM
// would provide:
//
//   * Java toolchain (Java 25) + consistent compiler flags       (<properties>)
//   * JUnit 5 test setup + a dedicated integrationTest source set (<build>)
//   * JaCoCo coverage collection (unit + integration)             (jacoco plugin)
//   * Spotless (google-java-format) + Checkstyle for style        (plugins)
//
// Every module applies exactly this plugin and nothing more. If you are a
// Maven developer: read this file once and you know the whole build.
// ===========================================================================

import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    java
    jacoco
    id("com.diffplug.spotless")
    checkstyle
}

// Shared test-tooling versions live here rather than in the version catalog:
// precompiled script plugins cannot use the type-safe `libs` accessor without a
// fragile hack, and these are build-infrastructure versions (the "parent POM"),
// not application dependencies. Functional deps stay in gradle/libs.versions.toml.
val junitBomVersion = "5.11.4"
val assertjVersion = "3.27.3"
val jacocoVersion = "0.8.13"
val googleJavaFormatVersion = "1.28.0"
val checkstyleToolVersion = "10.21.1"

group = "io.github.marciomarinho.quotient"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    withSourcesJar()
}

repositories {
    mavenCentral()
}

// ---------------------------------------------------------------------------
// Dedicated integration-test source set (Testcontainers-backed). Lives beside
// unit tests but runs separately so `test` stays fast and CI can gate them
// independently. Coverage is collected for both.
// ---------------------------------------------------------------------------
val integrationTest: SourceSet = sourceSets.create("integrationTest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

configurations["integrationTestImplementation"].extendsFrom(configurations.testImplementation.get())
configurations["integrationTestRuntimeOnly"].extendsFrom(configurations.testRuntimeOnly.get())

val integrationTestTask = tasks.register<Test>("integrationTest") {
    description = "Runs integration tests (Testcontainers)."
    group = "verification"
    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath
    shouldRunAfter(tasks.test)
    useJUnitPlatform()
}

// ---------------------------------------------------------------------------
// Common test configuration.
// ---------------------------------------------------------------------------
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = TestExceptionFormat.FULL
        showStackTraces = true
    }
    // Virtual-thread-friendly and reproducible.
    jvmArgs("-XX:+EnableDynamicAgentLoading")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all,-processing", "-Werror"))
}

dependencies {
    "testImplementation"(platform("org.junit:junit-bom:$junitBomVersion"))
    "testImplementation"("org.junit.jupiter:junit-jupiter")
    "testImplementation"("org.assertj:assertj-core:$assertjVersion")
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

// ---------------------------------------------------------------------------
// Coverage. `check` depends on both test tasks; the aggregate report is wired
// at the root. Individual verification gates are configured per-module where
// the thresholds differ (see rating-engine / ledger-service).
// ---------------------------------------------------------------------------
jacoco {
    toolVersion = jacocoVersion
}

tasks.named("check") {
    dependsOn(integrationTestTask)
}

tasks.withType<JacocoReport>().configureEach {
    reports {
        xml.required = true
        html.required = true
    }
}

// ---------------------------------------------------------------------------
// Formatting + style. `make lint` runs `spotlessCheck` + `checkstyleMain`.
// ---------------------------------------------------------------------------
spotless {
    java {
        googleJavaFormat(googleJavaFormatVersion)
        importOrder()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
        target("src/**/*.java")
    }
}

checkstyle {
    toolVersion = checkstyleToolVersion
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = false
    maxWarnings = 0
}

// Checkstyle over generated/none-existent dirs shouldn't fail configuration.
tasks.withType<Checkstyle>().configureEach {
    // Style is checked on main + test; integrationTest inherits via source set.
    reports {
        xml.required = false
        html.required = true
    }
}
