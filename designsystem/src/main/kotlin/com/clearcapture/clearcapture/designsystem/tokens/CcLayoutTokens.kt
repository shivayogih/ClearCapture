package com.clearcapture.clearcapture.designsystem.tokens

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing scale (DESIGN_SYSTEM.md section 4): 4, 8, 12, 16, 24, 32, 48 dp.
 *
 * @property xs 4 dp.
 * @property sm 8 dp; also the minimum gap between adjacent touch targets.
 * @property md 12 dp.
 * @property lg 16 dp.
 * @property xl 24 dp.
 * @property xxl 32 dp.
 * @property xxxl 48 dp.
 * @property screenEdge Padding at the screen edge: 16 dp on phones, 24 dp on tablets.
 * @property targetGap Minimum gap between adjacent touch targets: 8 dp.
 */
@Immutable
public data class CcSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val xxxl: Dp = 48.dp,
    val screenEdge: Dp = 16.dp,
    val targetGap: Dp = 8.dp,
)

/** Spacing for a phone ([isTablet] false) or a tablet. */
internal fun ccSpacing(isTablet: Boolean): CcSpacing = CcSpacing(screenEdge = if (isTablet) 24.dp else 16.dp)

/**
 * Corner shapes (DESIGN_SYSTEM.md section 4): small 8, medium 12, large 16, pill 28 dp when
 * rounded; all square when the host chooses square corners. [circle] is used for the shutter and
 * icon buttons and does not follow the corner style.
 *
 * @property small 8 dp corners: chips inside cards, text fields.
 * @property medium 12 dp corners: buttons, check rows.
 * @property large 16 dp corners: cards.
 * @property pill 28 dp corners: banners and chips on the camera.
 * @property circle Round controls.
 */
@Immutable
public data class CcShapes(
    val small: Shape,
    val medium: Shape,
    val large: Shape,
    val pill: Shape,
    val circle: Shape = CircleShape,
)

/** Shapes for rounded ([rounded] true) or square corners. */
internal fun ccShapes(rounded: Boolean): CcShapes = if (rounded) {
    CcShapes(
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        pill = RoundedCornerShape(28.dp),
    )
} else {
    val square = RoundedCornerShape(0.dp)
    CcShapes(small = square, medium = square, large = square, pill = square)
}

/**
 * Fixed sizes (DESIGN_SYSTEM.md section 4). A host cannot change them.
 *
 * @property minTouchTarget Minimum touch target: 56 dp square.
 * @property shutterVisual Visible shutter diameter: 80 dp.
 * @property shutterTarget Shutter touch area: 96 dp.
 * @property iconInControl Icon size in controls: 24 dp.
 * @property iconInBanner Icon size in status banners: 32 dp.
 * @property guideStroke Guide line width: 3 dp.
 * @property guideOutline Outline drawn on each side of a guide line: 1 dp.
 * @property guideBracket Length of each guide corner bracket: 32 dp.
 * @property profileCardMinHeight Minimum height of a profile card: 88 dp.
 * @property buttonMinHeight Minimum height of primary and secondary buttons: 56 dp.
 * @property border Width of borders and dividers: 1 dp.
 * @property selectedBorder Width of the border of a selected card: 2 dp.
 */
@Immutable
public data class CcSizes(
    val minTouchTarget: Dp = 56.dp,
    val shutterVisual: Dp = 80.dp,
    val shutterTarget: Dp = 96.dp,
    val iconInControl: Dp = 24.dp,
    val iconInBanner: Dp = 32.dp,
    val guideStroke: Dp = 3.dp,
    val guideOutline: Dp = 1.dp,
    val guideBracket: Dp = 32.dp,
    val profileCardMinHeight: Dp = 88.dp,
    val buttonMinHeight: Dp = 56.dp,
    val border: Dp = 1.dp,
    val selectedBorder: Dp = 2.dp,
)

/**
 * Animation durations (DESIGN_SYSTEM.md section 5). When the system "remove animations" setting
 * is on ([reduced]), every duration is 0 so transitions are instant.
 *
 * @property fastMillis Button press, chip change: 120 ms.
 * @property standardMillis Hint change, guide colour change: 200 ms.
 * @property slowMillis Screen transitions: 320 ms.
 * @property reduced Whether the system asks for no animations.
 */
@Immutable
public data class CcMotion(
    val fastMillis: Int = 120,
    val standardMillis: Int = 200,
    val slowMillis: Int = 320,
    val reduced: Boolean = false,
)

/** Motion tokens, all 0 ms when [reduced]. */
internal fun ccMotion(reduced: Boolean): CcMotion =
    if (reduced) CcMotion(fastMillis = 0, standardMillis = 0, slowMillis = 0, reduced = true) else CcMotion()
