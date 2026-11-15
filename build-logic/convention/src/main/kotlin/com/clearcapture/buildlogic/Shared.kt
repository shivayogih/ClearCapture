package com.clearcapture.buildlogic

import com.android.build.api.dsl.CommonExtension
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

internal const val BASE_PACKAGE = "com.clearcapture.clearcapture"
internal const val RESOURCE_PREFIX = "cc_"

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.version(alias: String): String = findVersion(alias).get().requiredVersion

internal fun VersionCatalog.library(alias: String) = findLibrary(alias).get()

/**
 * Bytecode level for this module: apps use the build JDK (21); SDK libraries use `sdkBytecode` (17) so a host
 * building on JDK 17 can still read them. The compilers always run on the JDK 21 daemon.
 */
internal fun Project.bytecodeLevel(): String =
    if (pluginManager.hasPlugin("com.android.application")) libs.version("jdk") else libs.version("sdkBytecode")

internal fun Project.javaVersion(): JavaVersion = JavaVersion.toVersion(bytecodeLevel())

/** Settings shared by the application and every Android library. */
internal fun Project.configureAndroidCommon(extension: CommonExtension) {
    extension.apply {
        compileSdk = libs.version("compileSdk").toInt()
        defaultConfig.minSdk = libs.version("minSdk").toInt()
        compileOptions.sourceCompatibility = javaVersion()
        compileOptions.targetCompatibility = javaVersion()
        lint.warningsAsErrors = false
        lint.abortOnError = true
        lint.checkDependencies = false
        testOptions.unitTests.isIncludeAndroidResources = true
        testOptions.unitTests.isReturnDefaultValues = true
    }
    configureKotlinCompiler()
    configureDetekt()
    registerModuleRulesCheck()
    registerRawDesignValuesCheck()
}

/** Kotlin compiler options shared by Android and JVM modules. */
internal fun Project.configureKotlinCompiler() {
    val explicitApi = path == ":capture:api" || path == ":capture:compose" || path == ":capture:testing"
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(bytecodeLevel()))
            // Compile against the API of the target level, not the JDK 21 the compiler runs on.
            freeCompilerArgs.add("-Xjdk-release=${bytecodeLevel()}")
            allWarningsAsErrors.set(false)
            if (explicitApi && !name.contains("Test")) {
                freeCompilerArgs.add("-Xexplicit-api=strict")
            }
        }
    }
}

/** detekt with one shared configuration file for the whole project. */
internal fun Project.configureDetekt() {
    pluginManager.apply("io.gitlab.arturbosch.detekt")
    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        parallel = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        source.setFrom(
            files("src/main/kotlin", "src/main/java", "src/test/kotlin", "src/androidTest/kotlin", "src/debug/kotlin"),
        )
    }
    // detekt 1.x bundles its own Kotlin compiler; keep it on the version it was built against.
    val detektKotlin = libs.version("detektKotlin")
    configurations.matching { it.name == "detekt" }.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") useVersion(detektKotlin)
        }
    }
    tasks.withType<Detekt>().configureEach {
        jvmTarget = libs.version("sdkBytecode")
        reports.html.required.set(true)
        reports.xml.required.set(false)
        reports.txt.required.set(false)
        reports.sarif.required.set(false)
        reports.md.required.set(false)
    }
    tasks.matching { it.name == "check" }.configureEach { dependsOn("detekt") }
}

/** `:capture:api` becomes `com.clearcapture.clearcapture.api`; `:app` is the base package itself. */
internal fun Project.moduleNamespace(): String =
    if (path == ":app") BASE_PACKAGE else "$BASE_PACKAGE.${name.replace("-", "")}"
