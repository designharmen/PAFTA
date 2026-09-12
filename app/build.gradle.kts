plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.harmen.pafta"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.harmen.pafta"
        // API 26 covers every tablet PAFTA targets and is the floor for the
        // Filament renderer we add in the 3D phase.
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        // The build label is what makes a device report unambiguous. Without it,
        // "I installed the new one and still see the old message" cannot be
        // told apart from "I tested the previous APK" — and a slow device round
        // gets spent on the wrong question. The APK workflow passes GitHub's run
        // number; a build made by hand says so.
        versionName = "0.1.0"
        buildConfigField(
            "String",
            "BUILD_LABEL",
            "\"${project.findProperty("paftaBuild") ?: "yerel"}\"",
        )
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets["main"].java.srcDirs("src/main/kotlin")

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/LICENSE*",
        )
    }
}

dependencies {
    implementation(project(":core:geometry"))
    implementation(project(":core:units"))
    implementation(project(":core:measure"))
    implementation(project(":core:dxf"))
    implementation(project(":core:project"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.documentfile)
    implementation(libs.kotlinx.coroutines.android)
    // Güncelleme denetimi GitHub'ın yanıtını okuyor; kütüphane zaten katalogda.
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
