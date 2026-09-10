plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":core:geometry"))
    testImplementation(libs.kotlin.test)
}
