plugins {
    id("clearcapture.android.application")
    id("clearcapture.android.compose")
    id("clearcapture.hilt")
    id("clearcapture.test")
}

android {
    defaultConfig {
        applicationId = "com.clearcapture.clearcapture"
        versionCode = 1
        versionName = libs.versions.sdkVersion.get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        debug {
            // Emulator support for the POC only; the SDK itself ships arm64-v8a.
            ndk {
                abiFilters += "x86_64"
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // The POC is never distributed; the debug key lets release builds be measured on a device.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation(project(":capture:api"))
    implementation(project(":designsystem"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.compose.material3)
}
