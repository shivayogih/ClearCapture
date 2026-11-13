plugins {
    id("clearcapture.android.library")
    id("clearcapture.android.compose")
    id("clearcapture.test")
    alias(libs.plugins.kotlin.parcelize)
}

dependencies {
    implementation(project(":capture:ui"))
    implementation(project(":capture:domain"))
    implementation(project(":capture:data"))
    implementation(project(":core:camera"))
    implementation(project(":core:opencv"))
    implementation(project(":core:quality"))
    implementation(project(":core:location"))
    implementation(project(":core:common"))
    implementation(project(":designsystem"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(testFixtures(project(":capture:domain")))
    testImplementation(libs.robolectric)
}
