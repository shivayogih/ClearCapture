package com.clearcapture.clearcapture.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import com.clearcapture.clearcapture.designsystem.tokens.CcColors
import com.clearcapture.clearcapture.designsystem.tokens.CcMotion
import com.clearcapture.clearcapture.designsystem.tokens.CcShapes
import com.clearcapture.clearcapture.designsystem.tokens.CcSizes
import com.clearcapture.clearcapture.designsystem.tokens.CcSpacing
import com.clearcapture.clearcapture.designsystem.tokens.CcTypography
import com.clearcapture.clearcapture.designsystem.tokens.DarkColors
import com.clearcapture.clearcapture.designsystem.tokens.LightColors
import com.clearcapture.clearcapture.designsystem.tokens.ccMotion
import com.clearcapture.clearcapture.designsystem.tokens.ccShapes
import com.clearcapture.clearcapture.designsystem.tokens.ccSpacing
import com.clearcapture.clearcapture.designsystem.tokens.ccTypography

private const val TABLET_MIN_WIDTH_DP = 600

internal val LocalCcColors = staticCompositionLocalOf { LightColors }
internal val LocalCcTypography = staticCompositionLocalOf { ccTypography(null) }
internal val LocalCcSpacing = staticCompositionLocalOf { CcSpacing() }
internal val LocalCcShapes = staticCompositionLocalOf { ccShapes(rounded = true) }
internal val LocalCcSizes = staticCompositionLocalOf { CcSizes() }
internal val LocalCcMotion = staticCompositionLocalOf { CcMotion() }
internal val LocalCcIsTablet = staticCompositionLocalOf { false }

/**
 * The SDK's theme. Every SDK screen is wrapped in it once, at its root; it never reads the host's
 * `MaterialTheme`. Inside, the Material 3 components that the design system wraps see a colour
 * scheme, type scale and shapes built from the SDK's own tokens.
 *
 * @param appearance What the host chose to change. A brand pair below 4.5:1 is refused and the
 *   default primary colours are used.
 * @param onBrandColorRejected Called once for each refused brand pair, so the caller can log it.
 * @param content The SDK content.
 */
@Composable
public fun ClearCaptureTheme(
    appearance: CcAppearance = CcAppearance(),
    onBrandColorRejected: (CcBrandColorRejection) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val dark = when (appearance.darkMode) {
        CcDarkMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        CcDarkMode.LIGHT -> false
        CcDarkMode.DARK -> true
    }
    ClearCaptureThemeContent(
        appearance = appearance,
        dark = dark,
        reduceMotion = rememberSystemReduceMotion(),
        isTablet = LocalConfiguration.current.smallestScreenWidthDp >= TABLET_MIN_WIDTH_DP,
        onBrandColorRejected = onBrandColorRejected,
        content = content,
    )
}

/** [ClearCaptureTheme] with every environment input explicit, for tests. */
@Composable
internal fun ClearCaptureThemeContent(
    appearance: CcAppearance,
    dark: Boolean,
    reduceMotion: Boolean,
    isTablet: Boolean,
    onBrandColorRejected: (CcBrandColorRejection) -> Unit,
    content: @Composable () -> Unit,
) {
    val resolved = remember(appearance.brandColor, appearance.onBrandColor, dark) {
        resolveColors(if (dark) DarkColors else LightColors, appearance.brandColor, appearance.onBrandColor)
    }
    val currentOnRejected by rememberUpdatedState(onBrandColorRejected)
    LaunchedEffect(resolved.rejection) {
        resolved.rejection?.let { currentOnRejected(it) }
    }
    val typography = remember(appearance.fontFamily) { ccTypography(appearance.fontFamily) }
    val shapes = remember(appearance.cornerStyle) { ccShapes(appearance.cornerStyle == CcCornerStyle.ROUNDED) }
    val spacing = remember(isTablet) { ccSpacing(isTablet) }
    val motion = remember(reduceMotion) { ccMotion(reduceMotion) }
    val colors = resolved.colors
    val colorScheme = remember(colors) { colors.toMaterialColorScheme() }
    val materialTypography = remember(typography) { typography.toMaterialTypography() }
    val materialShapes = remember(shapes) { shapes.toMaterialShapes() }

    CompositionLocalProvider(
        LocalCcColors provides colors,
        LocalCcTypography provides typography,
        LocalCcSpacing provides spacing,
        LocalCcShapes provides shapes,
        LocalCcSizes provides CcSizes(),
        LocalCcMotion provides motion,
        LocalCcIsTablet provides isTablet,
    ) {
        MaterialTheme(colorScheme = colorScheme, typography = materialTypography, shapes = materialShapes) {
            content()
        }
    }
}

/** Access to the current design tokens inside [ClearCaptureTheme]. */
public object CcTheme {

    /** The colour roles of the active palette, including the camera overlay colours. */
    public val colors: CcColors
        @Composable @ReadOnlyComposable
        get() = LocalCcColors.current

    /** The type scale. */
    public val type: CcTypography
        @Composable @ReadOnlyComposable
        get() = LocalCcTypography.current

    /** The spacing scale, including the screen-edge padding for this device. */
    public val spacing: CcSpacing
        @Composable @ReadOnlyComposable
        get() = LocalCcSpacing.current

    /** Corner shapes, following the host's corner style. */
    public val shapes: CcShapes
        @Composable @ReadOnlyComposable
        get() = LocalCcShapes.current

    /** Fixed sizes: touch targets, shutter, icons and guide lines. */
    public val sizes: CcSizes
        @Composable @ReadOnlyComposable
        get() = LocalCcSizes.current

    /** Animation durations; all 0 when the system asks for no animations. */
    public val motion: CcMotion
        @Composable @ReadOnlyComposable
        get() = LocalCcMotion.current

    /** Whether the device is a tablet (smallest width at least 600 dp). */
    public val isTablet: Boolean
        @Composable @ReadOnlyComposable
        get() = LocalCcIsTablet.current
}
