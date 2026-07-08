// Compiles the convention plugin(s) under src/main/kotlin as precompiled
// script plugins. The plugin dependencies below are what the convention script
// is allowed to `apply`.

plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    // Make the Spotless plugin applicable from the convention script.
    // Version comes from the shared catalog.
    implementation(libs.spotless.gradle)
}
