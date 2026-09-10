plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":core:geometry"))
    api(project(":core:units"))
    testImplementation(libs.kotlin.test)
}
