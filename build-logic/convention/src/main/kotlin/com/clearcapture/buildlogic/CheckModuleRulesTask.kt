package com.clearcapture.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * Fails the build when a module breaks the architecture rules in [ModuleRules]:
 * a project dependency the HLD does not allow, an injection framework in an SDK module,
 * or a non-Kotlin dependency in a pure Kotlin module.
 */
abstract class CheckModuleRulesTask : DefaultTask() {

    @get:Input
    abstract val modulePath: Property<String>

    @get:Input
    abstract val projectDependencies: SetProperty<String>

    @get:Input
    abstract val externalGroups: SetProperty<String>

    @get:Input
    abstract val appliedInjectionPlugins: SetProperty<String>

    @TaskAction
    fun check() {
        val path = modulePath.get()
        val problems = mutableListOf<String>()

        val allowed = ModuleRules.allowedProjectDependencies[path]
        if (allowed == null) {
            problems += "$path is not listed in ModuleRules. Add it to docs/HLD.md section 3 and to ModuleRules."
        } else {
            (projectDependencies.get() - allowed).sorted().forEach { forbidden ->
                problems += "$path must not depend on $forbidden (docs/HLD.md section 3)."
            }
        }

        if (path in ModuleRules.sdkModules) {
            appliedInjectionPlugins.get().sorted().forEach { plugin ->
                problems += "$path applies '$plugin'. SDK modules must not use an injection framework."
            }
            externalGroups.get().intersect(ModuleRules.injectionDependencyGroups).sorted().forEach { group ->
                problems += "$path depends on '$group'. SDK modules must not use an injection framework."
            }
        }

        if (path in ModuleRules.pureKotlinModules) {
            (externalGroups.get() - ModuleRules.pureKotlinAllowedGroups).sorted().forEach { group ->
                problems += "$path is a pure Kotlin module and must not depend on '$group'."
            }
        }

        if (problems.isNotEmpty()) {
            throw GradleException("Architecture rules broken:\n" + problems.joinToString("\n") { " - $it" })
        }
    }
}

/** Registers `checkModuleRules` and makes `check` depend on it. */
internal fun Project.registerModuleRulesCheck() {
    val task = tasks.register("checkModuleRules", CheckModuleRulesTask::class.java) {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Checks this module against the architecture rules in the HLD."
        modulePath.set(project.path)
    }
    afterEvaluate {
        val production = configurations.filter { !it.name.contains("test", ignoreCase = true) }
        val declared = production.flatMap { it.dependencies }
        task.configure {
            projectDependencies.set(
                declared.filterIsInstance<ProjectDependency>().map { it.path }.filter { it != project.path }.toSet(),
            )
            externalGroups.set(
                declared.filterIsInstance<ExternalModuleDependency>().mapNotNull { it.group }.toSet(),
            )
            appliedInjectionPlugins.set(
                ModuleRules.injectionPluginIds.filter { pluginManager.hasPlugin(it) }.toSet(),
            )
        }
    }
    tasks.matching { it.name == LifecycleBasePlugin.CHECK_TASK_NAME }.configureEach { dependsOn(task) }
}
