plugins {
    id("clearcapture.android.library")
    id("clearcapture.test")
}

android {
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    // FrameMats and MatPool expose org.opencv types, so consumers compile against them.
    api(libs.opencv)
    api(libs.kotlinx.coroutines.core)

    // JVM unit tests use the desktop natives; the Android AAR is removed from their classpath below.
    testImplementation(libs.opencv.desktop)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}

// The Android AAR and the desktop jar both contain org.opencv.*; JVM unit tests must only see the desktop jar,
// whose natives load on Windows, macOS and Linux (docs/VERSIONS.md section 4).
configurations.configureEach {
    if (name.endsWith("UnitTestRuntimeClasspath") || name.endsWith("UnitTestCompileClasspath")) {
        exclude(group = "org.opencv", module = "opencv")
    }
}
