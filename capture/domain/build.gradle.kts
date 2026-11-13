plugins {
    id("clearcapture.kotlin.library")
    id("clearcapture.test")
}

dependencies {
    api(project(":core:common"))
    implementation(libs.kotlinx.coroutines.core)
}
