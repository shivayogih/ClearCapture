# ClearCapture SDK: Host Requirements and Integration Contract

| | |
|---|---|
| SDK | ClearCapture (`com.clearcapture.clearcapture`) |
| Audience | Developers of apps that embed the SDK, and the SDK team |
| Status | Draft for review, revision 2 (13 November 2026) |
| Companions | `HLD.md`, `LLD.md`, `DESIGN_SYSTEM.md` |

This document is the contract between the SDK and any app that uses it: what the host app must provide, what the SDK does for it, and who is responsible for what. Nothing here has been built yet; figures marked "target" must be confirmed during implementation.

## 1. What the SDK gives a host app

One call opens a guided camera flow. The SDK checks the photograph for clarity, exposure, glare, framing, resolution and obstruction, writes and verifies the capture location inside the image file, and returns either an accepted image with its quality report or a clear reason why not. Everything runs on the device.

The host does not need Compose, Hilt, Kotlin coroutines knowledge or any camera code. A Java-only app with XML layouts can use it.

## 2. Artifacts

| Artifact | Contents | Who needs it |
|---|---|---|
| `com.clearcapture:clearcapture` | The whole SDK: capture flow in its own Activity, public API, design system, OpenCV | Every host |
| `com.clearcapture:clearcapture-compose` | A navigation destination so the flow can live inside the host's Compose navigation graph (module `:capture:compose`; depends on `clearcapture`) | Only hosts that want in-graph navigation |
| `com.clearcapture:clearcapture-testing` | Fakes: a launcher that returns canned results without a camera (module `:capture:testing`; depends on `clearcapture`) | Host UI tests |

The internal modules (`:capture:api`, `:capture:ui`, `:capture:domain`, `:capture:data`, `:core:*`, `:designsystem`) are published as one AAR so hosts see a single dependency and cannot reach internals. The plan is to use the Android Gradle Plugin's fused-library publishing for this; that must be verified against the resolved AGP version in the packaging milestone, with separate internal artifacts under the same version as the fallback.

## 3. What the host app must provide

### 3.1 Build requirements

| Requirement | Value | Notes |
|---|---|---|
| `minSdk` | 26 or higher (decided; `VERSIONS.md` section 8) | The SDK's own `minSdk` is the floor for every host |
| `compileSdk` | Equal to or higher than the SDK's | AGP 9 enforces this for library consumers |
| AndroidX | Required | |
| Java / Kotlin | Java 17 toolchain. Kotlin is not required in the host | Kotlin hosts need a compiler that can read the SDK's Kotlin metadata; the supported range is listed per release |
| Compose | Not required in the host | Compose is an internal dependency of the SDK. Hosts that also use Compose must be on a version compatible with the SDK's; the supported range is listed per release |
| ABIs | `arm64-v8a` | On other ABIs the SDK reports `UNSUPPORTED_ABI` instead of crashing |
| R8 | Nothing to add | Consumer rules ship inside the AAR |

### 3.2 Device requirements

| Requirement | If missing |
|---|---|
| Back camera | `NO_CAMERA_HARDWARE`; the flow does not open and the host receives `Failed(NOT_READY)` |
| GPS or another location provider | `NO_LOCATION_PROVIDER`; for a profile that requires location the flow does not open and the host receives `Failed(NOT_READY)` |
| Location services switched on | The SDK asks the user to turn them on |
| About 50 MB free storage | `LOW_STORAGE`; a launch gives `Failed(STORAGE_FULL)` |
| Google Play Services | Optional. Without it the SDK uses the platform GPS provider |

The SDK declares camera and GPS hardware as not required in its manifest, so adding it never hides the host app from devices on Google Play. The host decides whether to require them.

## 4. Initialisation

```kotlin
// Optional. Call once, for example in Application.onCreate. Cheap: it stores config only.
ClearCapture.initialize(
    context,
    ClearCaptureConfig(
        permissionMode = PermissionMode.SDK_MANAGED,
        appearance = Appearance(brandColor = 0xFF0B6B53),
    )
)
```

- If the host never calls `initialize`, the SDK initialises itself with defaults on first launch of the flow.
- `initialize` does no disk, camera, location or OpenCV work. Heavy work starts only when the flow opens.
- No `Application` subclass, no content-provider auto-start, no background services, no work scheduled.
- Safe to call more than once; the last configuration before a launch wins.

## 5. Permissions

This is the area most likely to go wrong in a host app, so the rules are explicit.

### 5.1 What the SDK declares

The SDK's manifest adds these, and the host's merged manifest will contain them:

| Permission | Why | When requested |
|---|---|---|
| `CAMERA` | Preview and capture | When the flow opens |
| `ACCESS_FINE_LOCATION` | Precise capture location in the image | When the flow opens, for profiles that require location |
| `ACCESS_COARSE_LOCATION` | Required alongside fine location on Android 12 and later | Same request |

The SDK never requests or needs: background location, storage or media permissions (images are written to app-private storage), microphone, notifications, or internet.

### 5.2 Two modes

| | `SDK_MANAGED` (default) | `HOST_MANAGED` |
|---|---|---|
| Who asks the user | The SDK, inside its flow | The host, before launching |
| Rationale screens | The SDK's, in the SDK's design system; text can be overridden | The host's |
| If something is missing at launch | The SDK asks | The flow does not open; the host receives `Failed(PERMISSION_MISSING)` with the list |
| Use when | You want it to work with one call | Your app has its own permission onboarding or a policy about when to ask |

In both modes the host can call `ClearCapture.checkReadiness(context)` at any time to see what is missing without showing any UI (section 6).

### 5.3 Behaviour in every case (SDK-managed mode)

| Situation | What the SDK does |
|---|---|
| First request | Shows a short reason screen, then the system dialog. Camera first, then location |
| User denies once | Explains what will not work and offers to ask again |
| User denies permanently, or the system stops showing the dialog | Explains, and offers a button to the app's system settings. On return, rechecks automatically |
| User grants only approximate location | Explains that precise location is needed for evidence and asks for the upgrade. If refused: profiles that require location are blocked; if the user leaves, the host receives `Cancelled`, as it does whenever the user leaves the flow. `LOCATION_PRECISE_NOT_GRANTED` is never a result: it is a blocker reported by `checkReadiness` and listed in the `message` of `Failed(PERMISSION_MISSING)` in host-managed mode |
| User grants "only this time" | Works for this run; asked again next time, as the system dictates |
| Permission revoked while the flow is open | The system restarts the process; on return the flow starts again at the permission step. No partial image is returned |
| Location services off | Asks the user to turn them on, with a route to the system setting. Rechecks on return |
| Camera disabled by device policy | `CAMERA_DISABLED_BY_POLICY`; no request is shown because it cannot succeed, and the host receives `Failed(NOT_READY)` |
| User leaves at the permission step | The host receives `Cancelled` |
| Profile does not require location (`location.required = false` in the profile) | Location is not requested at all. All four bundled profiles require location |
| Permissions already granted | No permission screen is shown |

### 5.4 What the host must do

- Do not remove the SDK's permissions from the merged manifest. If the host manifest removes one, readiness reports `PERMISSION_REMOVED_FROM_MANIFEST` and a launch gives `Failed(NOT_READY)`.
- If the host already declares these permissions, there is no conflict; manifest merging keeps one entry.
- If the host adds a `maxSdkVersion` to any of them, the SDK stops working above that level.
- Explain camera and location use in the host's privacy policy and store listing (section 9).
- In `HOST_MANAGED` mode: request both permissions, with precise location, before launching, and handle the `Failed(PERMISSION_MISSING)` result.

## 6. Readiness check

```kotlin
val readiness = ClearCapture.checkReadiness(context, profileId = "container")
if (!readiness.isReady) { /* readiness.blockers tells you why */ }
```

Fast, synchronous, no UI, no side effects: it changes nothing and shows nothing, though it may read. The profile ID is checked only when `profileId` is passed, by asking the configured profile provider on the calling thread; a host-supplied `CaptureProfileProvider` is therefore a blocking, Java-callable interface and should answer from memory. The "no work at app start" rule (section 4) is about app start, not about an explicit `checkReadiness` call. Blockers:

| Blocker | Meaning | Who can fix it |
|---|---|---|
| `NO_CAMERA_HARDWARE` | No back camera | Nobody |
| `CAMERA_DISABLED_BY_POLICY` | Device policy blocks the camera | Device administrator |
| `CAMERA_PERMISSION_MISSING` | Not granted | User (SDK or host asks) |
| `LOCATION_PERMISSION_MISSING` | Not granted | User |
| `LOCATION_PRECISE_NOT_GRANTED` | Only approximate granted | User |
| `LOCATION_SERVICES_OFF` | Location switched off | User |
| `NO_LOCATION_PROVIDER` | No usable provider on the device (fatal when the profile requires location) | Nobody |
| `LOW_STORAGE` | Not enough free space | User |
| `UNSUPPORTED_ABI` | No native library for this processor | SDK team |
| `PROFILE_NOT_FOUND` | The profile ID is unknown | Host developer |
| `PERMISSION_REMOVED_FROM_MANIFEST` | The host manifest removed a required permission | Host developer |

Missing permissions and location services being off are not fatal in `SDK_MANAGED` mode: the SDK resolves them with the user. In `HOST_MANAGED` mode any blocker stops the launch. The result each blocker gives at launch is in section 12.

## 7. Launching and receiving the result

```kotlin
// Kotlin
private val capture = registerForActivityResult(EvidenceCaptureContract()) { result ->
    when (result) {
        is CaptureResult.Accepted  -> upload(result.imageUri, result.location, result.quality)
        is CaptureResult.Cancelled -> Unit
        is CaptureResult.Failed    -> show(result.code, result.message)
    }
}
capture.launch(CaptureConfig(profileId = "container"))
```

- The same contract works from Java and from Fragments.
- The result survives rotation and process death because it is delivered through the Activity Result API.
- `Accepted.imageUri` is a `file://` URI to a file in the host app's private storage. The file belongs to the host from that moment: the host uploads it, keeps it or deletes it.
- If the host needs to hand the file to another app, `ClearCapture.getShareableUri(context, imageUri)` returns a `content://` URI from the SDK's own file provider (authority `${applicationId}.clearcapture.files`, so it cannot clash with the host's).
- `CaptureConfig.outputDirectory`, when set, must be inside the app's private storage (`filesDir`, `cacheDir` or the app's external files directory). Anything else is an invalid configuration and throws `IllegalArgumentException` when the launch contract creates its Intent.
- One capture per launch. To take several, launch again.

## 8. Customisation

| What | How | Limits |
|---|---|---|
| Profiles and thresholds | Supply a `CaptureProfileProvider` (a blocking interface, callable from Java; the SDK calls it off the main thread except from `checkReadiness`), or ship a JSON file | Must pass schema validation; otherwise the bundled defaults are used and an error is logged. `location.required` alone decides whether a profile needs location; the `LOCATION` check is derived from it |
| Brand colour, dark mode, corner style, font | `Appearance` in `ClearCaptureConfig` | Status colours and camera-overlay colours are fixed for readability and cannot be changed; a brand colour with too little contrast is rejected and the default used |
| Text and languages | Override any `cc_` string resource in the host app; add translations the same way | |
| Where records are stored | Supply a `CaptureRepository` | The opt-in diagnostics then read attempts from it |
| Capturing without a location fix | Supply a profile with `location.required = false` | There is no "override without a fix" in 0.1.0 |
| Logging and analytics | Supply `CaptureLogger`, `CaptureAnalytics`, or a `CaptureEventListener` | The SDK sends nothing anywhere by itself |
| Accept-on-warning, override-on-fail | Per profile, or per launch in `CaptureConfig` | |

## 9. Data, privacy and compliance: who is responsible

| Topic | SDK | Host app |
|---|---|---|
| Network | Makes no network calls and declares no internet permission | Uploads images if it wants to |
| Where images live | Writes to the host's app-private storage; nothing in the shared gallery | Retention, deletion, backup rules |
| Location | Reads it only while the flow is open; writes it into the image and the capture record | Tells users, and decides whether to strip it before sharing an image outside the organisation |
| Photographs of people | Captures what the user points at; no face detection, no identification | Consent, notices and lawful basis where people are photographed |
| Privacy policy and store disclosures | Provides the facts in this table | Declares camera, precise location and photo collection in its privacy policy and in the Google Play Data safety form |
| Analytics | None built in | Anything it adds through the listener |
| Mock locations | Detects and flags them on the result | Decides what to do with a flagged capture |
| Children, regulated sectors | No special handling | Its own obligations |

## 10. Effect on the host's build

| Area | Effect |
|---|---|
| Manifest | Adds the three permissions in section 5.1, two optional hardware features, one Activity (not exported), one file provider (not exported). Sets no application-level attributes |
| APK size | OpenCV native libraries dominate. Target: report the exact figure per ABI in each release; use ABI splits or app bundles so users download one ABI |
| Native libraries | 16 KB page-size compatible (verified per release) |
| Method count and startup | No work at app start; nothing runs until the flow opens |
| Transitive dependencies | CameraX, Compose runtime, OpenCV, Room, ExifInterface, Play Services Location. Listed with versions in each release so hosts can align or resolve conflicts |
| Resources | All SDK resources carry the `cc_` prefix |
| Themes | The SDK always uses its own theme (`ClearCaptureTheme`), in its Activity and in the navigation destination; it never uses, inherits or alters the host's |
| Licences | OpenCV is Apache 2.0. The SDK ships a notices file; hosts that show open-source notices should include it |

## 11. Versioning and compatibility

- Semantic versioning. A major version may break the API; minor versions add; patch versions fix.
- Only types in the `com.clearcapture.clearcapture.api` package are public. Everything else can change in any release.
- Anything marked with an opt-in annotation (diagnostics, experimental) can change in a minor release.
- A deprecated API keeps working for at least two minor releases before removal in the next major.
- Every release publishes: changelog, API diff, supported `minSdk`/`compileSdk`/Kotlin/Compose ranges, transitive dependency versions, size per ABI.
- The profile JSON has its own `schemaVersion`. The SDK reads the current and the previous schema.
- Binary compatibility of the public API is checked in the build; an unintended change fails it.

## 12. Results the host must handle

| Result | Code | Meaning | Suggested host action |
|---|---|---|---|
| `Accepted` | | Image passed, or was accepted with a warning or an override | Use the image |
| `Cancelled` | | The user left the flow at any point, including the permission step | Nothing |
| `Failed` | `PERMISSION_MISSING` | Host-managed mode and something was not granted. `message` lists the blockers, which may include `LOCATION_PRECISE_NOT_GRANTED` | Ask, then relaunch |
| `Failed` | `NOT_READY` | A fatal blocker: no camera, camera disabled by policy, unsupported ABI, a permission removed from the manifest, or no location provider when the profile requires location. In host-managed mode also location services switched off. `message` lists the blockers | Offer another way to proceed |
| `Failed` | `CAMERA_ERROR` | The camera could not be opened or used | Retry later |
| `Failed` | `STORAGE_FULL` | Storage was low at launch, or the image could not be saved | Ask the user to free space |
| `Failed` | `PROFILE_NOT_FOUND` | Unknown profile ID | Fix the integration |
| `Failed` | `INTERNAL` | Unexpected error; details in the log | Report to the SDK team |

`LOCATION_PRECISE_NOT_GRANTED` is never a result code. It is only a `Blocker` reported by `checkReadiness` and an entry in the `message` of `Failed(PERMISSION_MISSING)` in host-managed mode.

The SDK never throws into the host for a runtime condition. Exceptions are reserved for programmer errors such as an invalid configuration object, for example a `CaptureConfig.outputDirectory` outside the app's private storage.

## 13. Host integration checklist

1. Add the dependency and build. Confirm the merged manifest contains the three permissions.
2. Decide the permission mode. For `HOST_MANAGED`, add your own requests before launch.
3. Call `checkReadiness` on the screen that offers capture; disable or explain as needed.
4. Register the contract and handle `Accepted`, `Cancelled` and every `Failed` code.
5. Decide what happens to accepted files: upload, retention, deletion.
6. Set `Appearance` and override any text you want changed.
7. Supply your own profiles if the defaults do not fit.
8. Update the privacy policy and the Data safety form.
9. Test on a real device: first run, denied, permanently denied, approximate-only, location off, rotation, process death during capture, low storage.
10. Test a minified release build.

## 14. SDK release checklist (SDK team)

1. Public API diff reviewed; version number matches the kind of change.
2. Sample hosts build and run against the published artifact, not the project modules: a Java + XML host, a Kotlin + Compose host, a host with Hilt, and a host in `HOST_MANAGED` mode.
3. Minified sample host passes the capture journey.
4. Permission matrix in section 5.3 tested on the lowest and highest supported Android versions.
5. Size per ABI and 16 KB page-size check recorded.
6. Transitive dependency list and supported version ranges published.
7. Changelog, integration guide and this document updated.
8. Notices file current.
