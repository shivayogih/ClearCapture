plugins {
    id("clearcapture.android.library")
    id("clearcapture.android.compose")
    id("clearcapture.test")
}

dependencies {
    implementation(project(":capture:domain"))
    implementation(project(":core:camera"))
    implementation(project(":core:common"))
    implementation(project(":designsystem"))

    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(testFixtures(project(":capture:domain")))
}
