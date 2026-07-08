// build-logic is an included build that produces exactly one plugin:
// `quotient.java-conventions`. Keeping it separate means the convention code is
// compiled once and reused across all modules, and it can read the root
// version catalog below.

dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
