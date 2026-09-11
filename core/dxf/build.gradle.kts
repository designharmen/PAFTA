import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":core:geometry"))
    testImplementation(libs.kotlin.test)
}

// Bu blok her çekirdek modülde tekrar ediyor; gerekçesi kök dosyada yazılı.
kotlin {
    compilerOptions {
        // The core modules are consumed by the Android app, so they emit JVM 11
        // bytecode. No Gradle toolchain is declared: this keeps the build working
        // with whatever JDK is already installed rather than provisioning another.
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging { events("passed", "failed", "skipped") }
}
