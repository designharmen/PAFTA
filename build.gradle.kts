import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// Shared configuration for every pure-Kotlin core module.
//
// We deliberately target JVM 11 bytecode instead of declaring a Gradle
// toolchain: the core modules are consumed by the Android app, and this keeps
// the build working with whatever JDK is already installed (Android Studio's
// bundled JBR, or the JDK on CI) without provisioning a second one.
subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        // `api(...)` dependencies need the java-library plugin.
        apply(plugin = "java-library")
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_11)
                freeCompilerArgs.add("-Xjvm-default=all")
            }
        }
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            testLogging { events("passed", "failed", "skipped") }
        }
    }
}
