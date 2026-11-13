import com.android.build.api.dsl.ApplicationExtension
import com.clearcapture.buildlogic.configureAndroidCommon
import com.clearcapture.buildlogic.libs
import com.clearcapture.buildlogic.moduleNamespace
import com.clearcapture.buildlogic.version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Android application module (the POC app and sample hosts). Kotlin comes from AGP's built-in support. */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        extensions.configure<ApplicationExtension> {
            namespace = moduleNamespace()
            defaultConfig.targetSdk = libs.version("targetSdk").toInt()
            configureAndroidCommon(this)
        }
    }
}
