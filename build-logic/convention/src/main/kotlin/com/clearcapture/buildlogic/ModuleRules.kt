package com.clearcapture.buildlogic

/**
 * The module graph the HLD (section 3) allows. The build fails when a module declares a
 * dependency that is not listed here, so the code cannot drift from the design unnoticed.
 *
 * To add a module or an edge: change the HLD first, then this file, in the same commit.
 */
object ModuleRules {

    /** Project dependencies each module may declare in its production configurations. */
    val allowedProjectDependencies: Map<String, Set<String>> = mapOf(
        ":app" to setOf(":capture:api", ":designsystem"),
        ":capture:api" to setOf(
            ":capture:ui", ":capture:domain", ":capture:data",
            ":core:camera", ":core:opencv", ":core:quality", ":core:location", ":core:common",
            ":designsystem",
        ),
        ":capture:compose" to setOf(":capture:api"),
        ":capture:testing" to setOf(":capture:api"),
        ":capture:ui" to setOf(":capture:domain", ":core:camera", ":core:common", ":designsystem"),
        ":capture:domain" to setOf(":core:common"),
        ":capture:data" to setOf(":capture:domain", ":core:common"),
        ":core:camera" to setOf(":capture:domain", ":core:common"),
        ":core:opencv" to emptySet(),
        ":core:quality" to setOf(":capture:domain", ":core:opencv", ":core:common"),
        ":core:location" to setOf(":capture:domain", ":core:common"),
        ":core:common" to emptySet(),
        ":designsystem" to emptySet(),
        ":benchmark" to setOf(":app"),
    )

    /** Modules that ship inside the SDK. None of them may use an injection framework. */
    val sdkModules: Set<String> = allowedProjectDependencies.keys - setOf(":app", ":benchmark")

    /** Modules that must stay plain Kotlin: no Android, CameraX, OpenCV, Room or Hilt. */
    val pureKotlinModules: Set<String> = setOf(":capture:domain", ":core:common")

    /** The only external dependency groups a pure Kotlin module may use in production code. */
    val pureKotlinAllowedGroups: Set<String> = setOf("org.jetbrains.kotlin", "org.jetbrains.kotlinx")

    /** Gradle plugins that would force an injection framework on every host app. */
    val injectionPluginIds: Set<String> = setOf(
        "com.google.dagger.hilt.android",
        "dagger.hilt.android.plugin",
        "org.jetbrains.kotlin.kapt",
        "com.android.legacy-kapt",
        "com.squareup.anvil",
        "dev.zacsweers.metro",
    )

    /** Dependency groups that belong to an injection framework. */
    val injectionDependencyGroups: Set<String> = setOf(
        "com.google.dagger",
        "javax.inject",
        "jakarta.inject",
        "io.insert-koin",
        "org.kodein.di",
        "me.tatarka.inject",
        "dev.zacsweers.metro",
    )
}
