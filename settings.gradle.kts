pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ClearCapture"

include(":app")
include(":capture:api")
include(":capture:compose")
include(":capture:testing")
include(":capture:ui")
include(":capture:domain")
include(":capture:data")
include(":core:camera")
include(":core:opencv")
include(":core:quality")
include(":core:location")
include(":core:common")
include(":designsystem")
