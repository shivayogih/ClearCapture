plugins {
    id("clearcapture.android.library")
    id("clearcapture.android.compose")
    id("clearcapture.test")
}

dependencies {
    implementation(project(":capture:domain"))
    implementation(project(":core:common"))
}
