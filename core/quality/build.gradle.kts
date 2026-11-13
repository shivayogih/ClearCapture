plugins {
    id("clearcapture.android.library")
    id("clearcapture.test")
}

dependencies {
    implementation(project(":capture:domain"))
    implementation(project(":core:opencv"))
    implementation(project(":core:common"))
}
