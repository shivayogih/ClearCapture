import com.android.build.api.dsl.LibraryExtension
import com.clearcapture.buildlogic.RESOURCE_PREFIX
import com.clearcapture.buildlogic.configureAndroidCommon
import com.clearcapture.buildlogic.moduleNamespace
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Android library module of the SDK: own namespace, the `cc_` resource prefix and
 * consumer R8 rules, so every module is configured the same way.
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> {
            namespace = moduleNamespace()
            resourcePrefix = RESOURCE_PREFIX
            defaultConfig.consumerProguardFiles("consumer-rules.pro")
            configureAndroidCommon(this)
        }
    }
}
