import com.clearcapture.buildlogic.library
import com.clearcapture.buildlogic.libs
import io.github.takahirom.roborazzi.RoborazziExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * Screenshot tests with Roborazzi on the JVM (Robolectric). Apply after the library, Compose and
 * test conventions.
 *
 * - Golden images live in `src/test/screenshots` and are committed.
 * - `check` verifies them: it depends on `verifyRoborazziDebug`, which runs the debug unit tests
 *   in verify mode, so a changed pixel fails the build.
 * - Re-record after an intended change: `./gradlew :<module>:recordRoborazziDebug`.
 * - Screenshot tests live in a `screenshot` package and run only on the debug variant, which has
 *   the Compose test activity in its manifest.
 */
class ScreenshotTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("io.github.takahirom.roborazzi")
        extensions.configure<RoborazziExtension> {
            outputDir.set(layout.projectDirectory.dir("src/test/screenshots"))
        }
        dependencies {
            add("testImplementation", libs.library("robolectric"))
            add("testImplementation", libs.library("roborazzi"))
            add("testImplementation", libs.library("roborazzi-compose"))
            add("testImplementation", libs.library("compose-ui-test-junit4"))
            add("debugImplementation", libs.library("compose-ui-test-manifest"))
        }
        tasks.withType<Test>().configureEach {
            if (name.contains("Release")) exclude("**/screenshot/**")
            maxHeapSize = "2g"
        }
        tasks.matching { it.name == LifecycleBasePlugin.CHECK_TASK_NAME }.configureEach {
            dependsOn("verifyRoborazziDebug")
        }
    }
}
