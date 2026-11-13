plugins {
    id("clearcapture.android.library")
    id("clearcapture.test")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":capture:domain"))
    implementation(project(":core:common"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(testFixtures(project(":capture:domain")))
}
