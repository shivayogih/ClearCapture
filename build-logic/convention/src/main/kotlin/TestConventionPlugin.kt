import com.clearcapture.buildlogic.library
import com.clearcapture.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/** Unit-test setup shared by every module: JUnit 4, Truth, coroutines test, Turbine and MockK. */
class TestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        dependencies {
            add("testImplementation", libs.library("junit"))
            add("testImplementation", libs.library("truth"))
            add("testImplementation", libs.library("kotlinx-coroutines-test"))
            add("testImplementation", libs.library("turbine"))
            add("testImplementation", libs.library("mockk"))
        }
        pluginManager.withPlugin("com.android.base") {
            dependencies {
                add("androidTestImplementation", libs.library("androidx-test-ext-junit"))
                add("androidTestImplementation", libs.library("androidx-test-runner"))
                add("androidTestImplementation", libs.library("truth"))
            }
        }
        tasks.withType<Test>().configureEach {
            testLogging.events("failed", "skipped")
            testLogging.showStackTraces = true
        }
    }
}
