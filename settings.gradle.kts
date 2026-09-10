pluginManagement {
    repositories {
        // Google's Maven is required for AGP / AndroidX / Compose.
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "pafta"

// --- Pure-Kotlin core (no Android SDK required) -----------------------------
include(":core:geometry")
include(":core:units")
include(":core:measure")
include(":core:dxf")
include(":core:project")

// --- Android modules -------------------------------------------------------
// These require the Android SDK + Google's Maven repository. Environments that
// have neither (e.g. a locked-down CI container) can build and test the pure
// Kotlin core alone with:   ./gradlew -PpaftaCoreOnly=true test
val coreOnly = providers.gradleProperty("paftaCoreOnly").orNull?.toBoolean() ?: false
if (!coreOnly) {
    include(":app")
}
