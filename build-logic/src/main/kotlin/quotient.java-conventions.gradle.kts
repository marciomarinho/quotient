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

    // Testcontainers needs to find the Docker daemon. Docker Desktop uses a
    // non-standard per-user socket and exposes a CLI-proxy socket that rejects
    // /info with HTTP 400, which docker-java otherwise auto-selects. Point it at
    // the standard socket path — a symlink to the real socket on macOS and the
    // native socket on Linux CI — unless DOCKER_HOST is already set.
    environment("DOCKER_HOST", System.getenv("DOCKER_HOST") ?: "unix:///var/run/docker.sock")
    // docker-java's default API version is older than the minimum a recent Docker
    // Engine accepts, which makes /info return HTTP 400. It reads the version from
    // the `api.version` system property (not the DOCKER_API_VERSION env var), so
    // pin a version within the daemon's supported range (Docker 25+/CI: >= 1.44).
    systemProperty("api.version", System.getenv("DOCKER_API_VERSION") ?: "1.44")
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
    options.compilerArgs.add("-parameters")
    // Strict lint on our own code, but NOT on Spring AOT-generated sources
    // (compileAotJava / compileAotTestJava), which we do not control.
    // -serial: we never Java-serialize our exceptions, so serialVersionUID noise
    // is not worth -Werror failing the build.
    if (!name.contains("Aot", ignoreCase = true)) {
        options.compilerArgs.addAll(listOf("-Xlint:all,-processing,-serial", "-Werror"))
    }
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

// For Spring Boot application modules we keep the standard `jar` task ENABLED
// (it emits the "-plain" library jar). The GraalVM native-image build resolves
// the module's own classes from that plain jar, so disabling it breaks
// `nativeCompile` (the app's main class drops off the native classpath). To keep
// Docker unambiguous, the plain jar carries the `-plain` classifier by default,
// and the Dockerfiles COPY `build/libs/*-SNAPSHOT.jar` — a glob that matches only
// the executable bootJar, never the `-SNAPSHOT-plain.jar`. The (unused) sources
// jar is still disabled to avoid clutter.
plugins.withId("org.springframework.boot") {
    tasks.matching { it.name == "sourcesJar" }.configureEach { enabled = false }

    // Every service gets distributed tracing wired the same way: Micrometer
    // Tracing bridged to OpenTelemetry, exported over OTLP to the collector.
    // Versions are managed by each module's Spring Boot BOM.
    dependencies {
        "implementation"("io.micrometer:micrometer-tracing-bridge-otel")
        "implementation"("io.opentelemetry:opentelemetry-exporter-otlp")
    }
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

// The GraalVM native plugin adds `aot` / `aotTest` source sets of Spring-generated
// code (e.g. `Foo__BeanDefinitions`). That code is not ours to style, and its
// `__` names fail our TypeName rule, so skip Checkstyle on the AOT source sets.
// Detaching these also keeps the ordinary JVM `build` from triggering AOT.
tasks.matching { it.name.startsWith("checkstyle") && it.name.contains("Aot") }
    .configureEach { enabled = false }
