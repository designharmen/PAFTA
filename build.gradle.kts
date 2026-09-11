// PAFTA'nın kök yapılandırması.
//
// Burada bilerek `plugins {}` bloğu yok.
//
// No `plugins {}` block here, deliberately. Declaring `kotlin("jvm")` at the
// root — even with `apply false` — puts the Kotlin Gradle plugin on the root
// buildscript classpath, which every subproject inherits. `:app` then requests
// `kotlin("android")`, which ships in the same artifact, *with* a version, and
// Gradle refuses:
//
//   The request for this plugin could not be satisfied because the plugin is
//   already on the classpath with an unknown version, so compatibility cannot
//   be checked.
//
// Letting each module declare its own plugins keeps the JVM and Android Kotlin
// variants in separate resolution scopes. It also means a core-only build never
// resolves the Android Gradle Plugin at all, which is what allows
// `-PpaftaCoreOnly=true` to work on a machine with no access to Google's Maven
// repository.
//
// Each core module therefore carries its own small `kotlin`/`java` block. With
// five modules that repetition is cheaper than a buildSrc convention plugin;
// if the project grows past a handful, move it into one.

tasks.register("clean", Delete::class) {
    delete(layout.buildDirectory)
}
