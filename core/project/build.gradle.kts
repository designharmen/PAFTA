plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":core:geometry"))
    api(project(":core:units"))
    api(project(":core:measure"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test)
}
