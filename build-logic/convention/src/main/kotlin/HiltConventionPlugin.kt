import com.clearcapture.buildlogic.ModuleRules
import com.clearcapture.buildlogic.library
import com.clearcapture.buildlogic.libs
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Hilt with KSP. For the POC app and sample hosts only; refuses to apply to an SDK module. */
class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        if (path in ModuleRules.sdkModules) {
            throw GradleException("$path is an SDK module and must not use Hilt (docs/HLD.md section 3.2).")
        }
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("com.google.dagger.hilt.android")
        dependencies {
            add("implementation", libs.library("hilt-android"))
            add("ksp", libs.library("hilt-compiler"))
        }
    }
}
