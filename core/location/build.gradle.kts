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
    implementation(project(":capture:domain"))
    implementation(project(":core:common"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.playServices)

    androidTestImplementation(libs.kotlinx.coroutines.test)
}
