plugins {
    id("clearcapture.android.library")
    id("clearcapture.android.compose")
    id("clearcapture.test")
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
}
