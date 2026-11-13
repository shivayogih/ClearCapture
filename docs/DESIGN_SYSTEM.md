# ClearCapture SDK: UI Design System

| | |
|---|---|
| Module | `:designsystem` (`com.clearcapture.clearcapture.designsystem`) |
| Status | Draft for review, revision 2 (13 November 2026) |
| Companions | `HLD.md`, `LLD.md`, `SDK_REQUIREMENTS.md`; visual preview in `design_system_preview.html` (an illustration only) |

Every screen in the SDK is built only from the tokens and components in this document. Feature screens contain no colour values, sizes or text styles of their own. The SDK always uses its own theme, `ClearCaptureTheme`; it never uses the host's `MaterialTheme`.

The HTML preview is an illustration only. Where it differs from this document or from the LLD, the documents win; in particular, when the shutter is enabled is defined by the gating rule in LLD section 3.4, not by the preview.

## 1. Who we are designing for

A field worker, outdoors, often in direct sunlight, often wearing gloves, holding a tablet in one hand, in a hurry. That gives five principles:

1. **The camera is the screen.** Chrome is minimal and sits on scrims over the preview.
2. **One instruction at a time.** A single short hint, never a list.
3. **Readable in sunlight.** High contrast everywhere; nothing depends on subtle colour differences.
4. **Big targets.** Every control can be hit with a gloved thumb.
5. **Never colour alone.** Every status has an icon and words as well as a colour.

## 2. Colour

All text pairs below meet at least 4.5:1 contrast (WCAG AA); the ratios were computed, not estimated.

### 2.1 Light theme

| Token | Colour | Paired with | Colour | Contrast | Use |
|---|---|---|---|---|---|
| `primary` | `#0B6B53` | `onPrimary` | `#FFFFFF` | 6.5:1 | Main actions: Accept, Continue |
| `primaryContainer` | `#BFEBDD` | `onPrimaryContainer` | `#00382A` | 10.1:1 | Selected profile card, highlights |
| `surface` | `#FFFFFF` | `onSurface` | `#14181A` | 17.9:1 | Screen background and main text |
| `surfaceVariant` | `#EEF1F0` | `onSurfaceVariant` | `#3F4947` | 8.2:1 | Cards, secondary text |
| `success` | `#17703A` | `onSuccess` | `#FFFFFF` | 6.1:1 | Pass verdict badge |
| `successContainer` | `#CDEFD6` | `onSuccessContainer` | `#0A3D1E` | 10.0:1 | Pass row background |
| `warning` | `#8A5300` | `onWarning` | `#FFFFFF` | 6.3:1 | Warning verdict badge |
| `warningContainer` | `#FFE2A8` | `onWarningContainer` | `#3A2400` | 11.7:1 | Warning row background |
| `error` | `#B3261E` | `onError` | `#FFFFFF` | 6.5:1 | Fail verdict badge, destructive actions |
| `errorContainer` | `#FFDAD5` | `onErrorContainer` | `#5C0F0A` | 10.6:1 | Fail row background |
| `info` | `#1F5FA8` | `onInfo` | `#FFFFFF` | 6.4:1 | Neutral information |
| `outline` | `#6F7977` | on `surface` | `#FFFFFF` | 4.5:1 | Borders, dividers (non-text) |

### 2.2 Dark theme

| Token | Colour | Paired with | Colour | Contrast | Use |
|---|---|---|---|---|---|
| `primary` | `#7FD6BC` | `onPrimary` | `#00382A` | 7.7:1 |  |
| `primaryContainer` | `#005141` | `onPrimaryContainer` | `#BFEBDD` | 7.2:1 |  |
| `surface` | `#101415` | `onSurface` | `#E3E7E6` | 14.9:1 |  |
| `surfaceVariant` | `#232A29` | `onSurfaceVariant` | `#C2CAC7` | 8.8:1 |  |
| `success` | `#7EDB95` | `onSuccess` | `#00390F` | 7.8:1 |  |
| `successContainer` | `#0F5226` | `onSuccessContainer` | `#CDEFD6` | 7.5:1 |  |
| `warning` | `#FFC267` | `onWarning` | `#472A00` | 8.2:1 |  |
| `warningContainer` | `#5F3B00` | `onWarningContainer` | `#FFE2A8` | 7.9:1 |  |
| `error` | `#FFB4AB` | `onError` | `#690005` | 7.7:1 |  |
| `errorContainer` | `#8C1D18` | `onErrorContainer` | `#FFDAD5` | 7.0:1 |  |
| `info` | `#A8C8FF` | `onInfo` | `#00315F` | 7.7:1 |  |

The theme follows the system setting unless the host fixes it. Outdoors in bright light the light theme is easier to read; the review screen uses whichever is active.

### 2.3 Camera overlay

The camera screen is always dark-on-preview whatever the theme, because the background is the live scene. Text and icons never sit directly on the preview: they sit on a scrim.

| Token | Colour | Contrast on scrim over a white scene | Use |
|---|---|---|---|
| `camera.scrim` | `#000000` at 72 % | | Behind every banner, chip and control |
| `camera.onScrim` | `#FFFFFF` | 9.3:1 | Hint text, icons on the scrim |
| `camera.guideNeutral` | `#FFFFFF` | 9.3:1 | Guide while aiming |
| `camera.guideReady` | `#4ADE80` | 5.3:1 | Guide and banner accent when ready |
| `camera.guideWarning` | `#FFC53D` | 5.9:1 | Guide when a warning-level hint is shown (the shutter stays enabled) |
| `camera.guideProblem` | `#FF8A80` | 4.1:1 | Guide when a blocking hint is shown (the shutter is disabled) |

The contrast column is the worst case: the scrim over a pure white scene (`#474747`). Text on the scrim is always `camera.onScrim` (white), which must reach 4.5:1. The three status colours are used only for icons, banner accents and guide lines, never for text, so they must reach 3:1. Guide lines are drawn 3 dp wide with a 1 dp black outline so they stay visible over both bright and dark scenes.

### 2.4 Status mapping

| Status | Colour role | Icon | Word |
|---|---|---|---|
| Pass / Ready | success, `camera.guideReady` | Check in circle | "Ready", "Passed" |
| Warning | warning, `camera.guideWarning` | Triangle with exclamation mark | "Check", plus the reason |
| Fail / Blocked | error, `camera.guideProblem` | Cross in circle | "Retake", plus the reason |
| Waiting | info, `camera.guideNeutral` | Progress ring | "Waiting for location…" |

On the camera screen these are the four values of `GuideStatus` in the LLD: `Ready`, `Warning`, `Problem` and `Neutral`. A warning never disables the shutter; the full rule is in LLD section 3.4, which also holds the single definition of hint priority.

### 2.5 What a host can change

| Host can set | Host cannot change |
|---|---|
| `brandColor` and `onBrandColor` (replace `primary` / `onPrimary`; containers are derived) | Status colours (success, warning, error) |
| Light, dark or follow system | Camera overlay colours and scrim |
| Corner style: rounded (default) or square | Minimum touch-target sizes |
| Font family | Minimum text sizes |

If `brandColor` and `onBrandColor` have less than 4.5:1 contrast, the SDK logs an error and uses its default primary.

## 3. Typography

System font by default. Sizes are in sp and scale with the user's font setting up to 200 %; layouts must not clip at 200 %.

| Token | Size / line height | Weight | Use |
|---|---|---|---|
| `hint` | 24 / 30 | SemiBold | The live hint on the camera screen |
| `title` | 20 / 26 | SemiBold | Screen titles, verdict |
| `bodyStrong` | 16 / 24 | Medium | Check names, button labels |
| `body` | 16 / 24 | Regular | Explanations, advice |
| `label` | 14 / 20 | Medium | Chips, captions |
| `mono` | 12 / 16 | Regular, monospace | Debug overlay only; never in the shipped flow |

The smallest text a user sees is 14 sp. Hints are sentence case, at most six words, no full stop.

## 4. Spacing, shape and size

| Token | Value |
|---|---|
| Spacing scale | 4, 8, 12, 16, 24, 32, 48 dp |
| Screen edge padding | 16 dp phones, 24 dp tablets |
| Corner radius | small 8, medium 12, large 16, pill 28 dp |
| Minimum touch target | 56 × 56 dp |
| Shutter button | 80 dp diameter, 96 dp touch area |
| Icon size | 24 dp in controls, 32 dp in status banners |
| Gap between adjacent targets | at least 8 dp |
| Guide stroke | 3 dp, corner brackets 32 dp long |

No shadows on the camera screen; scrims separate layers. Cards on the review screen use `surfaceVariant` and no elevation.

## 5. Motion and haptics

| Token | Value | Use |
|---|---|---|
| `fast` | 120 ms | Button press, chip change |
| `standard` | 200 ms | Hint change, guide colour change |
| `slow` | 320 ms | Screen transitions |

- Hint text cross-fades; it never slides, so the eye does not have to chase it.
- The guide changes colour without changing size or position.
- When the system "remove animations" setting is on, all transitions become instant.
- Haptics: a light tick on shutter press; a short confirm on Pass; a double pulse on Fail. No haptic on live hint changes.

## 6. Components

Each component is a stateless composable in `:designsystem`. Names carry the `Cc` prefix.

| Component | Purpose | States | Accessibility |
|---|---|---|---|
| `CcShutterButton` | Take the photo | enabled, disabled (with reason), capturing (progress ring) | Label "Take photo"; when disabled, the reason is read out |
| `CcHintBanner` | The single live instruction | neutral, ready, warning, problem; each with icon and text | Announced politely when it changes, at most once every 2 s |
| `CcGuideOverlay` | Framing guide for container, portrait, vehicle | neutral, ready, warning, problem | Decorative; state is conveyed by the banner |
| `CcLocationChip` | Location status | acquiring, ready (accuracy shown), weak, off, no permission | Tappable when it can fix the problem (opens settings) |
| `CcIconButton` | Torch, close, back | default, pressed, toggled, disabled | 56 dp target, labelled |
| `CcProfileCard` | Choose what is being photographed | default, pressed, selected | Whole card is one target, minimum height 88 dp |
| `CcVerdictBadge` | Overall result on review | pass, warning, fail, loading | Icon + word; announced when it appears |
| `CcCheckRow` | One quality check: name, status, reason, advice | pass, warning, fail | Read as one sentence |
| `CcPrimaryButton` | Accept, Continue, Grant | enabled, disabled, loading | Minimum height 56 dp, full width on phones |
| `CcSecondaryButton` | Retake, Not now | enabled, disabled | Same size as primary |
| `CcPermissionCard` | Explain one permission before asking | default, granted, denied, permanently denied | Reason in plain words; one action |
| `CcStatusScreen` | Full-screen states: camera unavailable, storage full | info, error | Title, one sentence, one action |
| `CcTextField` | Override reason | default, focused, error | Label and character count |
| `CcScrim` | Background for anything over the preview | | |

### 6.1 Screen layouts

**Camera, landscape (tablet default):** preview fills the screen; hint banner top centre; location chip top left; close top right; shutter centred on the right edge, torch above it; guide centred.

**Camera, portrait:** hint banner top; location chip below it on the left; shutter bottom centre; torch bottom left; close top right.

Controls stay on the side of the user's thumb: shutter on the right edge in landscape, bottom centre in portrait. Nothing is placed in the system gesture areas.

**Review:** image on the left (landscape) or top (portrait), at least half the screen; verdict badge, then check rows, then actions. Retake and Accept are side by side, Accept on the thumb side; when Accept is not allowed it is replaced by an explanation, not shown disabled without a reason.

**Permission:** one card per permission, in the order they will be asked, each with the reason and its state.

## 7. Writing

| Do | Do not |
|---|---|
| "Hold steady" | "Image sharpness below threshold" |
| "Too dark. Move into the light or turn on the torch" | "Exposure validation failed" |
| "Fit the container in the frame" | "Invalid framing" |
| "Waiting for location…" | "GPS error" |
| "The photo is blurred. Hold the tablet steady and try again" | "Invalid image" |

Every rejection states what is wrong and what to do next. No technical terms, no codes, no blame.

## 8. Accessibility checklist

- Text contrast at least 4.5:1; large text and icons at least 3:1.
- Touch targets at least 56 dp.
- Works at 200 % font scale in both orientations.
- Every control has a content description; decorative graphics have none.
- State changes are announced; hints are rate-limited so a screen reader is not flooded.
- Status is never colour alone.
- Respects the system reduce-motion setting.
- Focus order follows the visual order; the shutter is reachable with a hardware key or switch access.

## 9. Implementation notes

- `:designsystem` depends only on Compose and Material 3. It has no dependency on any other SDK module, so it can be previewed and tested alone.
- The theme's input is the module's own `CcAppearance` type (brand colours, dark mode, corner style, font family). `:capture:api` maps the host's public `Appearance` to it, so `:designsystem` never refers to a `:capture:api` type.
- Tokens are exposed through one `ClearCaptureTheme` composable and a `CcTheme` accessor (`CcTheme.colors`, `CcTheme.type`, `CcTheme.spacing`, `CcTheme.shapes`, `CcTheme.motion`).
- Material 3 components are wrapped, never used directly in feature screens, so a Material update cannot change the SDK's look by surprise.
- Every component has previews for each state in light and dark, and a screenshot test (Roborazzi, run on the JVM).
- A lint rule or build check fails when a feature module uses a raw colour, a raw dp size for a touch target, or a Material component directly.
- The POC app includes a gallery screen that shows every component in every state; for this `:app` depends on `:designsystem` directly.
