plugins {
    id("clearcapture.kotlin.library")
    id("clearcapture.test")
    `java-test-fixtures`
}

dependencies {
    api(project(":core:common"))
    api(libs.kotlinx.coroutines.core)

    testFixturesImplementation(libs.kotlinx.coroutines.core)
}
