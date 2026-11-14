plugins {
    `kotlin-dsl`
}

group = "com.clearcapture.buildlogic"

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    implementation(libs.detekt.gradlePlugin)
    implementation(libs.roborazzi.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "clearcapture.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "clearcapture.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "clearcapture.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("kotlinLibrary") {
            id = "clearcapture.kotlin.library"
            implementationClass = "KotlinLibraryConventionPlugin"
        }
        register("hilt") {
            id = "clearcapture.hilt"
            implementationClass = "HiltConventionPlugin"
        }
        register("test") {
            id = "clearcapture.test"
            implementationClass = "TestConventionPlugin"
        }
        register("screenshot") {
            id = "clearcapture.screenshot"
            implementationClass = "ScreenshotTestConventionPlugin"
        }
    }
}
