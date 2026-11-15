# ClearCapture

An Android SDK that captures evidence photographs and accepts one only when it is technically good enough and carries valid location metadata. Everything runs on the device: no network, no ML models.

- Package: `com.clearcapture.clearcapture`
- Published as: `com.clearcapture:clearcapture` (from milestone 10)
- `:app` is a thin POC shell used to test and calibrate the SDK on real tablets.

## Getting started

1. Install the Android SDK (Android Studio is the easiest way) and set `ANDROID_HOME` to its folder.
2. Run the bootstrap once per checkout. It finds the SDK, writes the machine-specific `local.properties` (git-ignored), and checks the SDK packages the build needs:

   ```bash
   ./scripts/bootstrap.sh --install        # macOS, Linux
   ```

   ```powershell
   ./scripts/bootstrap.ps1 -Install        # Windows
   ```

   Android Studio users can simply open the project: it writes `local.properties` itself.
3. Build: `./gradlew check assembleDebug`.

You do not need to install a particular JDK. Gradle runs on **JDK 21**, pinned in `gradle/gradle-daemon-jvm.properties`, and downloads it automatically when no JDK 21 is installed. Apps are compiled for Java 21; the SDK libraries are compiled by JDK 21 to Java 17 bytecode (`sdkBytecode` in the catalog) so host apps that still build on JDK 17 can use them.

All tool and library versions are in [gradle/libs.versions.toml](gradle/libs.versions.toml) and explained in [docs/VERSIONS.md](docs/VERSIONS.md).

## Commands

| Task | Command |
|---|---|
| Everything: build, unit tests, detekt, Android lint, architecture rules | `./gradlew check assembleDebug` |
| Unit tests only | `./gradlew test` |
| Static analysis only | `./gradlew detekt` |
| Architecture rules only | `./gradlew checkModuleRules` |
| Install the POC on a device or emulator | `./gradlew :app:installDebug` |
| Release build of the POC | `./gradlew :app:assembleRelease` |

On Windows use `gradlew.bat`.

## Modules

```text
:app                POC shell: navigation host, debug tooling, DI root (Hilt)
:capture:api        The only module a host compiles against
:capture:compose    Optional Compose navigation destination (clearcapture-compose)
:capture:testing    Fakes for host tests (clearcapture-testing)
:capture:ui         Compose screens, ViewModels, MVI contracts
:capture:domain     Pure Kotlin: models, use cases, ports
:capture:data       Repositories, Room, profile loading
:core:camera        CameraX behind CameraController
:core:opencv        OpenCV loading, Mat conversion and pooling
:core:quality       The six validators and the score calculator
:core:location      Location source, fix rules, EXIF GPS
:core:common        Dispatchers, result types, logging interface
:designsystem       Tokens, theme and UI components
build-logic         Convention plugins and the architecture rules
```

The allowed dependencies between modules are defined once, in
[ModuleRules.kt](build-logic/convention/src/main/kotlin/com/clearcapture/buildlogic/ModuleRules.kt).
The build fails if a module depends on something the design does not allow, if an SDK module uses an
injection framework, or if a pure Kotlin module pulls in an Android dependency.

## Documents

| Document | Contents |
|---|---|
| [docs/HLD.md](docs/HLD.md) | High-level design |
| [docs/LLD.md](docs/LLD.md) | Low-level design |
| [docs/SDK_REQUIREMENTS.md](docs/SDK_REQUIREMENTS.md) | Contract with host apps |
| [docs/DESIGN_SYSTEM.md](docs/DESIGN_SYSTEM.md) | UI design system |
| [docs/VERSIONS.md](docs/VERSIONS.md) | Resolved versions and project decisions |
| [docs/DESIGN_REVIEW.md](docs/DESIGN_REVIEW.md) | Design review findings and the changes made |

The documents are the design. A code change that deviates from them updates them in the same pull request.

## Branches and CI

- `main` is protected and receives release merges only.
- `development` is the base branch. Work happens on `feature/*` branches and is merged through pull requests.
- Every pull request runs [CI](.github/workflows/ci.yml): build, unit tests, detekt, Android lint and the architecture rules.
- Instrumented tests run on an emulator [nightly and on demand](.github/workflows/instrumented.yml).
- Dependabot opens weekly update pull requests against `development`.
