# ClearCapture SDK: Host Requirements and Integration Contract

| | |
|---|---|
| SDK | ClearCapture (`com.clearcapture.clearcapture`) |
| Audience | Developers of apps that embed the SDK, and the SDK team |
| Status | Revision 4 (14 November 2026): single-screen multi-photo sessions, location modes, storage and delivery alerts (section 7) |
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
| `com.clearcapture:clearcapture-upload` | Offline-first upload queue on WorkManager; the host supplies the transport (module `:capture:upload`; depends on `clearcapture`). No network code, no internet permission | Hosts that want the SDK to manage uploads (section 7.1) |

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
| First request | A readiness panel on the capture screen lists what is missing with one reason each; one button asks for camera and location together in a single system step (revision 4) |
| User denies once | Explains what will not work and offers to ask again |
| User denies permanently, or the system stops showing the dialog | Explains, and offers a button to the app's system settings. On return, rechecks automatically |
| User grants only approximate location | Explains that precise location is needed for evidence and asks for the upgrade. If refused: profiles that require location are blocked; if the user leaves, the host receives `Cancelled`, as it does whenever the user leaves the flow. `LOCATION_PRECISE_NOT_GRANTED` is never a result: it is a blocker reported by `checkReadiness` and listed in the `message` of `Failed(PERMISSION_MISSING)` in host-managed mode |
| User grants "only this time" | Works for this run; asked again next time, as the system dictates |
| Permission revoked while the flow is open | The system restarts the process; on return the flow starts again at the permission step. No partial image is returned |
| Location services off | Asks the user to turn them on, with a route to the system setting. Rechecks on return |
| Camera disabled by device policy | `CAMERA_DISABLED_BY_POLICY`; no request is shown because it cannot succeed, and the host receives `Failed(NOT_READY)` |
| User leaves at the permission step | The host receives `Cancelled` |
| Location not wanted (`LocationMode.OFF`, or a profile with `location.required = false`) | Location is not requested at all. All four bundled profiles require location; `LocationMode` overrides them per launch |
| `LocationMode.OPTIONAL` and the worker refuses | Capture continues without GPS tags; each image says why (`locationNote`) |
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

## 7. Launching a capture session and receiving the result (revision 4)

Revision 4 replaces the one-photo, multi-screen flow. Owner decisions of 14 November 2026: one capture screen, several photos per session, the host sets how many and where they are saved, location is the host's choice, and every failure to store or deliver is shown to the worker.

```kotlin
// Kotlin. The Java form is the same through CaptureConfig.Builder.
private val capture = registerForActivityResult(EvidenceCaptureContract()) { result ->
    when (result) {
        is CaptureResult.Completed -> result.images.forEach { upload(it.uri, it.location, it.quality) }
        is CaptureResult.Cancelled -> keepOrDelete(result.images)   // photos kept before the worker left
        is CaptureResult.Failed    -> show(result.code, result.message)
    }
}
capture.launch(
    CaptureConfig(
        shots = listOf(Shot("container", label = "Bin front"), Shot("vehicle", label = "Truck")),
        minPhotos = 2,
        maxPhotos = 4,
        storage = StorageTarget.Directory(File(filesDir, "jobs/4711")),
        locationMode = LocationMode.REQUIRED,
        references = mapOf("jobId" to "4711", "binId" to "B-0093"),
    ),
)
```

### 7.0 The session

| Topic | Behaviour |
|---|---|
| Screens | One capture screen. Readiness problems, the per-photo verdict and alerts are panels on that screen; there is no profile picker and no separate review screen |
| Readiness | Checked together when the session opens and again whenever the app returns to the foreground: camera, permissions, location services, storage, profile. Everything the worker can fix is listed on the screen at once, with one action each; camera and location are requested in a single permission step |
| Subject type | `shots` lists what each photo is (subject profile and an optional label such as "Bin front"). The screen shows "Photo 2 of 4: Vehicle". Photos beyond the list, or every photo when the list is empty, use `defaultProfileId` (General when not set), and the worker can switch subject with the selector on the screen when `allowSubjectChange` is true |
| How many | `minPhotos` and `maxPhotos` (both 1 by default; equal values mean an exact count). The worker can finish once `minPhotos` photos are kept; the shutter stops at `maxPhotos` |
| After each photo | All checks run on the saved file at once (sharpness, exposure, glare, framing, resolution, obstruction, location). A pass is kept automatically and shown in the thumbnail strip. A warning or failure opens a panel with the exact reason and advice, and Keep (when the rules allow, with a reason for an override) or Retake. A kept photo can be removed from the strip before finishing |
| Finishing | "Done" when at least `minPhotos` are kept, or automatically at `maxPhotos`. Leaving earlier returns `Cancelled` with the photos already kept, so the host decides whether to keep them |
| Interruptions | Kept photos are stored and recorded as soon as they are kept. Rotation, a call, the app going to the background or the process being killed never loses them: when the session reopens it continues with them, and the result always contains them |

### 7.1a Location (host's choice)

| `LocationMode` | Permission | In the photo | `CapturedImage.location` |
|---|---|---|---|
| `REQUIRED` (default) | Asked by the SDK (or by the host in host-managed mode); capture blocked until a usable fix | GPS tags written and verified | Never null |
| `OPTIONAL` | Asked once; the worker may refuse | GPS tags when a fix is available | Null when unavailable; `locationNote` says why: `PERMISSION_DENIED`, `LOCATION_OFF`, `NO_FIX` |
| `OFF` | Never asked | No GPS tags | Always null (`locationNote = NOT_REQUESTED`) |

`LocationMode` overrides the profile's `location.required` for that launch.

### 7.1b What each photo carries

`CapturedImage`: `captureId`, `uri` (where it was saved, readable by the host), `profileId`, `label`, `quality` (verdict, score, every check with reason and metrics, override reason), `location` and `locationNote`, `capturedAtEpochMillis`, `timeSource` (`GPS` or `DEVICE`), `sha256` of the final file, `width`, `height`, `fileBytes`, and the host's `references`.

- `references` (job, bin, vehicle IDs, at most 16 entries of 128 characters) are written into the JPEG (`ImageDescription` and `UserComment` as JSON), so the photo identifies its job even outside the app.
- `output`: the host may cap the long side (for example 2560 px) and the JPEG quality to keep uploads small. Quality checks always run on the original, and EXIF is copied and verified after any re-encode.
- `sha256` lets a server detect a changed file.

### 7.1c Storage and delivery problems

| Situation | What the worker sees | What the host receives |
|---|---|---|
| Free space below 150 MB when the session opens | Warning banner: photos may not save | Nothing; capture continues |
| Free space below 50 MB | Shutter disabled; alert with "Free space, then try again" and "Finish with N photos" | If the worker finishes: `Completed` (min reached) or `Cancelled` with the kept photos |
| Writing to the host's destination fails (folder missing or not writable, sink error, media store refused) | Alert naming the problem, with Retry; the photo stays safe in SDK storage meanwhile | If it never succeeds: the image is delivered from SDK storage with `deliveryNote = STORED_IN_FALLBACK` and the failure text, never lost |
| A shareable link cannot be created | Not shown to the worker | `CapturedImage.uri` stays a file URI; `ClearCapture.getShareableUri` explains the failure in its exception, as a programming error |
### 7.1 Accessing, storing and uploading images (revision 3)

Hosts need to find their photographs again, keep them where their own rules say, and get them to a server. The SDK covers all three without network code of its own and without storage or internet permissions.

**Accessing images.** `ClearCapture.captures(context)` returns a `CaptureStore`, callable from Java:

| Call | What it does |
|---|---|
| `list(filter)` | Accepted captures, newest first, each with `captureId`, image URI, profile, verdict and checks, location, capture time and upload state. Filter by profile, time range and upload state |
| `get(captureId)` | One capture |
| `openFile(captureId)` | The JPEG as a `File`, for reading or copying |
| `shareableUri(captureId)` | A `content://` URI for handing to another app (same provider as `getShareableUri`) |
| `delete(captureId)`, `purgeOlderThan(epochMillis)` | Remove files and records; retention stays the host's decision |
| `observe(listener)` | Change notifications, for a host screen that lists captures |

A host that supplied its own `CaptureRepository` reads its own store; `CaptureStore` then reads through that repository.

**Where kept images are saved.** `CaptureConfig.storage` chooses per launch, and the result gives a readable URI for each image so the host can preview, copy or upload it:

| `StorageTarget` | Where the accepted JPEG goes | Permissions | Notes |
|---|---|---|---|
| `AppPrivate(subdirectory?)` (default) | `filesDir/cc_captures/yyyy/MM/dd/` or the named subfolder | None | Not visible to other apps or the gallery; removed with the app |
| `Directory(folder)` | The host's own folder, created if missing | None for the app's internal or external files directories; any other folder must be writable by the app without extra permissions | Checked when the session opens: a folder that cannot be written is reported to the worker at once, not after the first photo |
| `SharedPictures(album)` | MediaStore, `Pictures/<album>`, visible in the gallery | None on Android 10+; on Android 8–9 the host must hold `WRITE_EXTERNAL_STORAGE`, otherwise the result reports `Failed(STORAGE_FULL)` with the reason | EXIF and GPS tags are copied and verified after the copy. Location in a shared photo is visible to other apps: the host decides whether that is acceptable |
| `HostSink` | Wherever the host's `CaptureImageSink` puts it | The host's | For a Storage Access Framework folder, an encrypted store, or the host's own directory layout |

`CaptureImageSink` is a host interface set in `CaptureDependencies`. After accept, the SDK calls `store(image: AcceptedImage): SinkResult` on a background thread with the verified file and its metadata; the host copies or moves it and returns the final URI, or a failure that the SDK reports in the result. The app-private copy is kept until the sink succeeds, so nothing is lost if the host's storage is full.

**Uploading.** The core SDK makes no network calls. The optional artifact `com.clearcapture:clearcapture-upload` adds an offline-first upload queue:

- The host supplies an `UploadTransport` (its own HTTP client, cloud SDK, or signed-URL upload). The artifact has no network code and declares no internet permission; the host app already has both.
- `ClearCaptureUpload.enqueue(captureId)`, or `UploadPolicy.AUTOMATIC` to queue every accepted capture.
- Built on WorkManager: runs when connected (optionally unmetered or charging), retries with exponential backoff, survives restarts and reboots, uploads one file at a time with a configurable concurrency.
- Each capture carries an upload state (`NOT_QUEUED`, `QUEUED`, `UPLOADING`, `UPLOADED`, `FAILED`) and the last error, readable through `CaptureStore` and observable.
- Optional `deleteLocalAfterUpload` for devices with little storage.
- The transport receives the file plus a metadata object (profile, verdict, checks, location, times, device) so the server can store the evidence context with the image.

Hosts that already have an upload pipeline ignore the artifact and upload from the `Accepted` result or from `CaptureStore`.

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
| `Completed(images)` | | At least `minPhotos` photos were kept; each passed, or was kept with a warning or an override | Use the images |
| `Cancelled(images)` | | The worker left before finishing; `images` holds any photos already kept (often empty) | Keep or delete them |
| `Failed` | `PERMISSION_MISSING` | Host-managed mode and something was not granted | Ask, then relaunch |
| `Failed` | `NOT_READY` | A fatal blocker (no camera, unsupported ABI, policy) | Offer another way to proceed |
| `Failed` | `CAMERA_ERROR` | The camera could not be opened or used | Retry later |
| `Failed` | `STORAGE_FULL` | Not enough space to start a session | Ask the user to free space |
| `Failed` | `STORAGE_UNAVAILABLE` | The requested destination cannot be written (missing folder, no permission, media store unavailable) | Fix the destination |
| `Failed` | `PROFILE_NOT_FOUND` | A subject profile in `shots` or `defaultProfileId` is unknown | Fix the integration |
| `Failed` | `INTERNAL` | Unexpected error; details in the log | Report to the SDK team |

The SDK never throws into the host for a runtime condition. Exceptions are reserved for programmer errors such as an invalid configuration object (`minPhotos` above `maxPhotos`, more than 16 references).
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
