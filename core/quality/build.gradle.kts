import java.util.concurrent.Callable

plugins {
    id("clearcapture.android.library")
    id("clearcapture.test")
}

dependencies {
    implementation(project(":capture:domain"))
    implementation(project(":core:opencv"))
    implementation(project(":core:common"))

    testImplementation(testFixtures(project(":capture:domain")))
    // JVM unit tests use the desktop natives; the Android AAR is removed from their classpath below.
    testImplementation(libs.opencv.desktop)
}

// The Android AAR (via :core:opencv) and the desktop jar both contain org.opencv.*; JVM unit tests must
// only see the desktop jar (docs/VERSIONS.md section 4).
configurations.configureEach {
    if (name.endsWith("UnitTestRuntimeClasspath") || name.endsWith("UnitTestCompileClasspath")) {
        exclude(group = "org.opencv", module = "opencv")
    }
}

val fixturesDir: String = layout.projectDirectory.dir("src/test/resources/fixtures").asFile.absolutePath

// The fixture generator is test code (decision D22): it runs only through this task, never as part of `check`.
tasks.withType<Test>().configureEach {
    if (name != "generateFixtures") {
        filter.excludeTestsMatching("*GenerateFixtures")
    }
}

tasks.register<Test>("generateFixtures") {
    group = "verification"
    description = "Regenerates the synthetic fixture images in src/test/resources/fixtures."
    val unitTest = tasks.named<Test>("testDebugUnitTest")
    // Callables, not task providers: this task needs the compiled tests, not a run of testDebugUnitTest.
    testClassesDirs = files(Callable { unitTest.get().testClassesDirs })
    classpath = files(Callable { unitTest.get().classpath })
    filter.includeTestsMatching("*GenerateFixtures")
    systemProperty("clearcapture.fixturesDir", fixturesDir)
    outputs.upToDateWhen { false }
}
