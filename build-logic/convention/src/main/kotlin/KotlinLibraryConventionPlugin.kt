import com.clearcapture.buildlogic.configureDetekt
import com.clearcapture.buildlogic.configureKotlinCompiler
import com.clearcapture.buildlogic.javaVersion
import com.clearcapture.buildlogic.registerModuleRulesCheck
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure

/** Pure Kotlin (JVM) module: no Android on the classpath, so it compiles and tests as plain Kotlin. */
class KotlinLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = javaVersion()
            targetCompatibility = javaVersion()
        }
        configureKotlinCompiler()
        configureDetekt()
        registerModuleRulesCheck()
    }
}
