package com.clearcapture.clearcapture.designsystem.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Colour roles of the SDK (DESIGN_SYSTEM.md section 2). Every `onX` colour is the text and icon
 * colour to use on `x`; each pair reaches at least 4.5:1 contrast, which a unit test enforces.
 *
 * @property primary Main actions: Accept, Continue. Replaced by the host's brand colour when valid.
 * @property onPrimary Text and icons on [primary].
 * @property primaryContainer Selected profile card, highlights.
 * @property onPrimaryContainer Text and icons on [primaryContainer].
 * @property surface Screen background.
 * @property onSurface Main text on [surface].
 * @property surfaceVariant Cards.
 * @property onSurfaceVariant Secondary text on [surfaceVariant] and [surface].
 * @property success Pass verdict badge.
 * @property onSuccess Text and icons on [success].
 * @property successContainer Pass row background.
 * @property onSuccessContainer Text on [successContainer].
 * @property warning Warning verdict badge.
 * @property onWarning Text and icons on [warning].
 * @property warningContainer Warning row background.
 * @property onWarningContainer Text on [warningContainer].
 * @property error Fail verdict badge, destructive actions.
 * @property onError Text and icons on [error].
 * @property errorContainer Fail row background.
 * @property onErrorContainer Text on [errorContainer].
 * @property info Neutral information.
 * @property onInfo Text and icons on [info].
 * @property outline Borders and dividers (non-text, at least 3:1 on [surface]).
 * @property camera Colours of the camera overlay; the same in light and dark.
 * @property isDark Whether this is the dark palette.
 */
@Immutable
public data class CcColors(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val info: Color,
    val onInfo: Color,
    val outline: Color,
    val camera: CcCameraColors,
    val isDark: Boolean,
)

/**
 * Colours of everything drawn over the live camera preview (DESIGN_SYSTEM.md section 2.3).
 * Text and icons never sit directly on the preview, always on [scrim].
 *
 * @property scrim Behind every banner, chip and control: black at 72 %.
 * @property onScrim Hint text and icons on the scrim.
 * @property guideNeutral Guide and banner accent while aiming or waiting.
 * @property guideReady Guide and banner accent when ready.
 * @property guideWarning Guide and banner accent for a warning-level hint (the shutter stays enabled).
 * @property guideProblem Guide and banner accent for a blocking hint (the shutter is disabled).
 * @property guideOutline The 1 dp outline around guide lines, so they stay visible on bright scenes.
 */
@Immutable
public data class CcCameraColors(
    val scrim: Color,
    val onScrim: Color,
    val guideNeutral: Color,
    val guideReady: Color,
    val guideWarning: Color,
    val guideProblem: Color,
    val guideOutline: Color,
)

/** The camera overlay palette; it does not change with the theme or the host's appearance. */
internal val CameraColors: CcCameraColors = CcCameraColors(
    // 0xB8 = 184 = 72 % of 255. Over a white scene this composites to #474747.
    scrim = Color(0xB8000000),
    onScrim = Color(0xFFFFFFFF),
    guideNeutral = Color(0xFFFFFFFF),
    guideReady = Color(0xFF4ADE80),
    guideWarning = Color(0xFFFFC53D),
    guideProblem = Color(0xFFFF8A80),
    guideOutline = Color(0xFF000000),
)

/** Light palette, DESIGN_SYSTEM.md section 2.1. */
internal val LightColors: CcColors = CcColors(
    primary = Color(0xFF0B6B53),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBFEBDD),
    onPrimaryContainer = Color(0xFF00382A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14181A),
    surfaceVariant = Color(0xFFEEF1F0),
    onSurfaceVariant = Color(0xFF3F4947),
    success = Color(0xFF17703A),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFCDEFD6),
    onSuccessContainer = Color(0xFF0A3D1E),
    warning = Color(0xFF8A5300),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFFFE2A8),
    onWarningContainer = Color(0xFF3A2400),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD5),
    onErrorContainer = Color(0xFF5C0F0A),
    info = Color(0xFF1F5FA8),
    onInfo = Color(0xFFFFFFFF),
    outline = Color(0xFF6F7977),
    camera = CameraColors,
    isDark = false,
)

/**
 * Dark palette, DESIGN_SYSTEM.md section 2.2. The table there has no `outline`; `#899390`
 * is used (5.9:1 on the dark surface), recorded in the document.
 */
internal val DarkColors: CcColors = CcColors(
    primary = Color(0xFF7FD6BC),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF005141),
    onPrimaryContainer = Color(0xFFBFEBDD),
    surface = Color(0xFF101415),
    onSurface = Color(0xFFE3E7E6),
    surfaceVariant = Color(0xFF232A29),
    onSurfaceVariant = Color(0xFFC2CAC7),
    success = Color(0xFF7EDB95),
    onSuccess = Color(0xFF00390F),
    successContainer = Color(0xFF0F5226),
    onSuccessContainer = Color(0xFFCDEFD6),
    warning = Color(0xFFFFC267),
    onWarning = Color(0xFF472A00),
    warningContainer = Color(0xFF5F3B00),
    onWarningContainer = Color(0xFFFFE2A8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFFFDAD5),
    info = Color(0xFFA8C8FF),
    onInfo = Color(0xFF00315F),
    outline = Color(0xFF899390),
    camera = CameraColors,
    isDark = true,
)
