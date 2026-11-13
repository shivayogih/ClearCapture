plugins {
    id("clearcapture.android.library")
    id("clearcapture.android.compose")
    id("clearcapture.test")
}

dependencies {
    api(project(":capture:api"))
}
