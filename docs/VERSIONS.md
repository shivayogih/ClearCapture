# ClearCapture: resolved versions

| | |
|---|---|
| Resolved on | 13 November 2026 |
| Method | Latest stable read from each repository's `maven-metadata.xml`; release date is the `Last-Modified` header of the artifact's `.pom`. No version was taken from memory |
| Single source in the build | `gradle/libs.versions.toml` |

Repositories: **G** = Google Maven (`https://dl.google.com/android/maven2/`), **C** = Maven Central (`https://repo1.maven.org/maven2/`). The source URL of a row is `<repository>/<group as path>/<artifact>/maven-metadata.xml`.

## 1. Toolchain

| Component | Coordinates | Resolved | Released | Repo | Floor | Above floor |
|---|---|---|---|---|---|---|
| Android Gradle Plugin | `com.android.tools.build:gradle` | 9.5.1 | 2026-11-05 | G | 9.2.0 | Yes |
| Kotlin | `org.jetbrains.kotlin:kotlin-gradle-plugin` | 2.4.21 | 2026-10-08 | C | 2.3.10 | Yes |
| Gradle | wrapper | 9.9.1 | 2026-11-10 | [services.gradle.org](https://services.gradle.org/versions/current) | AGP needs ≥ 9.6.0 | Yes |
| KSP | `com.google.devtools.ksp:symbol-processing-gradle-plugin` | 2.3.13 | 2026-10-30 | C | n/a | n/a |
| JDK | toolchain | 17 | n/a | [AGP notes](https://developer.android.com/build/releases/gradle-plugin) | AGP minimum 17 | Equal |
| compileSdk / targetSdk | platform | 37 | n/a | [AGP notes](https://developer.android.com/build/releases/gradle-plugin) | 37 | Equal (AGP 9.5 maximum) |
| SDK Build Tools | | 36.0.0 | n/a | AGP notes | AGP minimum 36.0.0 | Equal |

## 2. Libraries

| Component | Coordinates | Resolved | Released | Repo | Floor | Above floor |
|---|---|---|---|---|---|---|
| Compose BOM | `androidx.compose:compose-bom` | 2026.09.00 | 2026-09-09 | G | 2026.08.00 | Yes |
| Compose UI / runtime / foundation | `androidx.compose.ui:ui` and siblings | 1.12.1 (from BOM) | 2026-09-09 | G | 1.12 | Yes |
| Material 3 | `androidx.compose.material3:material3` | from BOM | | G | n/a | n/a |
| CameraX | `androidx.camera:camera-core`, `-camera2`, `-lifecycle`, `-compose` | 1.6.2 | 2026-08-26 | G | 1.5.1 | Yes |
| OpenCV | `org.opencv:opencv` | 4.14.0 | see section 4 | C | 4.13.0 | Yes |
| Hilt (apps only) | `com.google.dagger:hilt-android` | 2.60.1 | 2026-07-06 | C | 2.59.2 | Yes |
| Coroutines | `org.jetbrains.kotlinx:kotlinx-coroutines-core`, `-android`, `-play-services`, `-test` | 1.11.0 | 2026-05-07 | C | n/a | n/a |
| Serialization | `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.13.0 | 2026-11-11 | C | n/a | n/a |
| Lifecycle | `androidx.lifecycle:lifecycle-*` | 2.11.0 | 2026-06-17 | G | n/a | n/a |
| Navigation for Compose | `androidx.navigation:navigation-compose` | 2.10.2 | 2026-09-23 | G | n/a | n/a |
| Room | `androidx.room:room-runtime`, `-ktx`, `-compiler`, `-testing` | 2.8.5 | 2026-09-09 | G | n/a | n/a |
| DataStore | `androidx.datastore:datastore-preferences` | 1.3.0 | 2026-10-21 | G | n/a | n/a |
| ExifInterface | `androidx.exifinterface:exifinterface` | 1.4.2 | 2025-12-03 | G | n/a | n/a |
| Play Services Location | `com.google.android.gms:play-services-location` | 21.4.0 | 2026-06-25 | G | n/a | n/a |
| Activity | `androidx.activity:activity-compose` | 1.13.0 | 2026-03-11 | G | n/a | n/a |
| Core | `androidx.core:core-ktx` | 1.19.1 | 2026-09-23 | G | n/a | n/a |
| AppCompat (Java sample host) | `androidx.appcompat:appcompat` | 1.8.0 | 2026-08-12 | G | n/a | n/a |
| Profile installer | `androidx.profileinstaller:profileinstaller` | 1.4.1 | 2024-10-02 | G | n/a | n/a |
| LeakCanary (debug) | `com.squareup.leakcanary:leakcanary-android` | 2.14 | 2024-04-18 | C | n/a | n/a |

## 3. Build, test and analysis

| Component | Coordinates | Resolved | Released | Repo |
|---|---|---|---|---|
| Baseline Profile plugin | `androidx.baselineprofile:androidx.baselineprofile.gradle.plugin` | 1.5.0 | 2026-09-09 | G |
| Macrobenchmark | `androidx.benchmark:benchmark-macro-junit4` | 1.5.0 | 2026-09-09 | G |
| JUnit | `junit:junit` | 4.13.2 | 2021-02-13 | C |
| Turbine | `app.cash.turbine:turbine` | 1.2.1 | 2025-06-11 | C |
| MockK | `io.mockk:mockk` | 1.14.12 | 2026-11-07 | C |
| Truth | `com.google.truth:truth` | 1.4.5 | 2025-09-10 | C |
| Robolectric | `org.robolectric:robolectric` | 4.17 | 2026-09-10 | C |
| Roborazzi (screenshot tests) | `io.github.takahirom.roborazzi:roborazzi` | 1.79.0 | 2026-11-12 | C |
| AndroidX Test | `androidx.test:core`, `:runner`, `:rules` 1.7.0; `androidx.test.ext:junit` 1.3.0; Espresso 3.7.0 | | 2025-07-30 | G |
| UI Automator | `androidx.test.uiautomator:uiautomator` | 2.4.1 | 2026-11-04 | G |
| detekt | `io.gitlab.arturbosch.detekt:detekt-gradle-plugin` | 1.23.8 | 2025-02-21 | C |
| Binary compatibility validator | `org.jetbrains.kotlinx:binary-compatibility-validator` | 0.18.2 | 2026-09-02 | C |
| Dokka | `org.jetbrains.dokka:dokka-gradle-plugin` | 2.3.0 | 2026-11-05 | C |

## 4. OpenCV: 4.14.0 chosen over 5.1.0

Both AARs were downloaded from Maven Central and inspected (ELF program headers of every `.so`).

| | 4.14.0 | 5.1.0 |
|---|---|---|
| AAR size | 117.7 MB | 141.1 MB |
| ABIs shipped | arm64-v8a, armeabi-v7a, x86, x86_64 | same |
| `arm64-v8a` `libopencv_java` | 23.5 MB | 31.2 MB |
| `armeabi-v7a` `libopencv_java` | 15.3 MB | 19.9 MB |
| 16 KB aligned: arm64-v8a, x86_64 (incl. `libc++_shared.so`) | Yes (`0x4000`) | Yes (`0x4000`) |
| 16 KB aligned: armeabi-v7a, x86 | No (`0x1000`); not required, 32-bit ABIs use 4 KB pages | same |
| `minSdk` | 21 | 21 |
| Licence | Apache-2.0 | Apache-2.0 |
| Java API | `org.opencv.*`, stable for years | Modules moved: geometry functions left `imgproc`, `features2d` renamed, `calib3d` split |

**Decision: 4.14.0.** It is 7.7 MB smaller per device on `arm64-v8a`, 5.1.0 was published three days before this check, and the 5.x module moves touch `imgproc` functions the validators use for no benefit to them. Re-evaluate at the next SDK minor release.

Unit tests run the validators on the JVM using desktop OpenCV natives (`org.openpnp:opencv`, same `org.opencv.*` Java API) so they need no emulator; an instrumented smoke test covers the real Android library.

## 5. Compatibility checks

| Check | Result |
|---|---|
| AGP 9.5.1 ↔ Gradle | Needs ≥ 9.6.0; 9.9.1 used |
| AGP 9.5.1 ↔ JDK | Needs 17; 17 used |
| AGP 9.5.1 ↔ compileSdk | Maximum 37; 37 used |
| Compose 1.12.1 ↔ compileSdk / AGP | AAR metadata: `minCompileSdk=37`, `minAndroidGradlePluginVersion=9.1.0`. Satisfied |
| Navigation 2.10.2, Core 1.19.1 | Same requirement (37 / AGP 9.1.0). Satisfied |
| CameraX 1.6.2 | `minCompileSdk=36`, `minSdk=23`. All `androidx.camera` artifacts on 1.6.2 |
| Kotlin 2.4.21 ↔ AGP built-in Kotlin | The Kotlin Gradle plugin 2.4.21 is put on the build classpath by `build-logic`; verified by the build in milestone 1 |
| KSP 2.3.13 ↔ Kotlin 2.4.21 | KSP 2.3.x release notes reference Kotlin 2.4 and AGP 9 built-in Kotlin fixes; verified by the Room and Hilt builds. Note: the 2.3.13 notes warn of a defect with concurrent KSP executions; watch for the next release |
| Hilt 2.60.1 ↔ AGP 9 | Used in `:app` and sample hosts only; verified by the build |
| detekt 1.23.8 ↔ Kotlin 2.4 | detekt 1.23.8 is built against Kotlin 2.0.21. Its own compiler dependency is pinned in the detekt configuration so it does not clash with the project's Kotlin. detekt 2.0 is alpha only (2.0.0-alpha.6) and is not used |

## 6. Findings for the design

- **CameraX Compose viewfinder is stable.** `androidx.camera:camera-compose` 1.6.2 (`CameraXViewfinder`) is a stable artifact. No fallback is needed; the fallback would have been `PreviewView` from `camera-view` inside `AndroidView`.
- **Single-AAR publishing.** The `com.android.fused-library` plugin exists in AGP 9.x and publishes with `from(components["fusedLibraryComponent"])`. Documented limits: AAR output only; no data binding; one build type and no flavors per fused library; duplicate classes or Java resources fail the build; resource conflicts are resolved by dependency order; transitive dependencies are listed in the POM, not packaged; Prefab is not supported. The documentation does not label it stable and marks the "publication only" restriction experimental. Compose, native libraries and resources are not listed as unsupported, so they must be proven by building. **Plan:** fuse the internal modules in milestone 10 and verify the AAR; **fallback:** publish each internal module as its own artifact under the same version with `clearcapture` depending on them.
- **Only pre-releases exist for:** detekt 2.x (using stable 1.23.8) and the Android Compose screenshot plugin (0.0.1-alpha16; using Roborazzi 1.79.0, which is stable and runs on the JVM).
- **16 KB deadline.** Google Play requires 16 KB support for updates from 1 February 2027 for apps targeting API 35+. The shipped ABIs pass.

## 7. Ranges a host needs to know

| | Value |
|---|---|
| `minSdk` | 26 |
| `compileSdk` | 37 or higher (required by Compose 1.12 inside the SDK) |
| Android Gradle Plugin | 9.1.0 or higher (required by Compose 1.12 AAR metadata) |
| Kotlin (Kotlin hosts only) | 2.3.0 or higher. The SDK is compiled with Kotlin 2.4, and a Kotlin compiler reads metadata one version ahead of itself |
| Compose (hosts that use Compose) | Compose BOM 2026.08.00 or higher (Compose 1.12.x). Gradle resolves to the higher of the host's and the SDK's version |
| Java-only hosts | Java 17 toolchain; no Kotlin plugin needed |

## 8. Project decisions

On 13 November 2026 the owner replied "all prompts are confirmed", without answering the seven questions individually. The documented assumptions are therefore adopted as decisions. Any of them can be changed later; the effect of each is in HLD section 1.4.

| # | Question | Decision |
|---|---|---|
| 1 | Lowest `minSdk` | 26 |
| 2 | First host apps and their Kotlin / Compose versions | Not known. The SDK supports the ranges in section 7 |
| 3 | ABIs | `arm64-v8a` in the SDK. `x86_64` is added to debug builds of the POC only, so it runs on an emulator |
| 4 | Accept a `Warning`; override a `Fail` | Warning can be accepted; Fail cannot be overridden (both remain profile flags) |
| 5 | Location fix limits | At most 60 s old and 50 m accurate |
| 6 | Storage and Play Services | App-private storage, retention is the host's; Play Services is used when present, platform GPS otherwise |
| 7 | Distribution and licence | Local Maven repository in the build; GitHub Packages from the release workflow. **Licence not decided** (open) |

## 9. Repository workflow

- `main` is protected and receives only release merges. `development` is the default and base branch.
- Work happens on `feature/*` branches merged into `development` through pull requests with CI passing.
