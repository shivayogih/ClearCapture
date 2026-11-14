# ClearCapture: build status

Updated with every milestone commit. Last update: 14 November 2026.

**Legend:** ✅ finished · 🔄 in progress · ⏳ pending · ⚠️ needs a person or a device

## Summary

| | Count |
|---|---|
| ✅ Finished | 6 of 14 prompts (1, 2, 3, 4 M1, 5 M2, 7 M4) |
| 🔄 In progress | M3 (screens and wiring); M5–M8 domain, data and ViewModel logic is done |
| ⏳ Pending | M9 performance, M10 packaging and sample hosts, final acceptance |
| Unit tests | 464 passing, 0 failing |
| Instrumented tests (emulator) | 9 passing (EXIF round trip 4, Room DAO 5); OpenCV smoke test not yet run |

## By prompt

| Prompt | Milestone | State | What is done | What is left |
|---|---|---|---|---|
| 1 | Context and rules | ✅ | Pack read; docs copied | |
| 2 | Versions | ✅ | `docs/VERSIONS.md`; JDK 21 build with auto-provisioning | Licence still undecided |
| 3 | Design review | ✅ | `docs/DESIGN_REVIEW.md`, 33 findings applied; LLD revision 5 notes | |
| 4 | M1 skeleton | ✅ | 13 modules, convention plugins, architecture and injection checks, CI, bootstrap scripts | |
| 5 | M2 design system | ✅ | Tokens, 14 components, 236 screenshot goldens, contrast tests, raw-value check, gallery | ⚠️ Gallery screenshots on a device |
| 6 | M3 permissions, readiness, preview, profiles | 🔄 | Profile JSON loader, readiness and launch rules, permission state machine (all 5.3 rows), CameraX binder and preview, all ViewModels | Screens being wired: SDK Activity, contract, navigation, `CaptureGraph`; ⚠️ device checks |
| 7 | M4 OpenCV and validators | ✅ | Six validators, analyzer, fixtures, timings, 16 KB check passed, release APK 25.5 MB with OpenCV | ⚠️ OpenCV smoke test on a device |
| 8 | M5 live validation and hints | 🔄 | Hint priority and stabiliser, live pipeline, guidance use case, gating rule | Wiring to the screen; adaptive step-down of analysis resolution; ⚠️ timings on a device |
| 9 | M6 capture and review | 🔄 | Capture, accept and discard use cases; review ViewModel; file store; text for every reason | Review screen wiring; haptics; ⚠️ capture on a device |
| 10 | M7 location | 🔄 | Location rules, fused and platform sources, EXIF store (verified on emulator) | Wiring; ⚠️ time-to-first-fix outdoors |
| 11 | M8 persistence and POC tooling | 🔄 | Room repository (verified on emulator) | `CaptureDiagnostics`, debug overlay, tuning screen, capture log, export, device capability screen |
| 12 | M9 performance | ⏳ | | Macrobenchmark, Baseline Profile, StrictMode, LeakCanary, ⚠️ measurements on a tablet |
| 13 | M10 SDK packaging | ⏳ | | Public API audit and dump, fused AAR, four sample hosts, `INTEGRATION.md`, `RELEASE.md` |
| 14 | Acceptance | ⏳ | | `ACCEPTANCE.md`, ⚠️ field run and calibration with your labelled photos |

## Branches

| Branch | Contents | State |
|---|---|---|
| `main` | Milestone 1 | Protected |
| `development` | Milestone 1, shared domain model, your Dependabot merges | Base branch |
| `feature/m5-m6-presentation` | Integration branch: M2, M3, M4 and the M5–M8 logic stacked | Pushed; PR to `development` once M3 is wired |

## Waiting on you

- Licence for the SDK.
- Whether `CaptureResult.Accepted.location` may be `null` for profiles without location (currently yes).
- Branch protection on `development` (could not be set from the agent).
- A real rugged tablet for every ⚠️ item.
