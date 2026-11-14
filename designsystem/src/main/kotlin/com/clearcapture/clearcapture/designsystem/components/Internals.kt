package com.clearcapture.clearcapture.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.clearcapture.clearcapture.designsystem.icons.CcIcons
import com.clearcapture.clearcapture.designsystem.theme.CcTheme

private const val RING_PERIOD_MILLIS = 1_000
private const val RING_SWEEP_DEGREES = 270f
private const val RING_START_DEGREES = -90f
private const val FULL_TURN_DEGREES = 360f

/** Alpha of disabled content (Material guidance; disabled controls are exempt from contrast rules). */
internal const val DISABLED_ALPHA: Float = 0.38f

/** Alpha of the overlay drawn on a pressed control. */
internal const val PRESSED_OVERLAY_ALPHA: Float = 0.16f

/** A decorative, tinted vector icon. Its meaning is always given in text or semantics elsewhere. */
@Composable
internal fun CcIconImage(icon: ImageVector, tint: Color, size: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = rememberVectorPainter(icon),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(size),
    )
}

/**
 * An indeterminate progress ring. It turns while animations are allowed and stands still when the
 * system asks for no animations. The rotation is read only while drawing, so it does not recompose.
 */
@Composable
internal fun CcProgressRing(color: Color, size: Dp, modifier: Modifier = Modifier, strokeWidth: Dp = 3.dp) {
    val rotation: State<Float>? = if (CcTheme.motion.reduced) {
        null
    } else {
        rememberInfiniteTransition(label = "CcProgressRing").animateFloat(
            initialValue = 0f,
            targetValue = FULL_TURN_DEGREES,
            animationSpec = infiniteRepeatable(tween(RING_PERIOD_MILLIS, easing = LinearEasing), RepeatMode.Restart),
            label = "CcProgressRingRotation",
        )
    }
    Canvas(modifier = modifier.size(size)) {
        val width = strokeWidth.toPx()
        rotate(rotation?.value ?: 0f) {
            drawArc(
                color = color,
                startAngle = RING_START_DEGREES,
                sweepAngle = RING_SWEEP_DEGREES,
                useCenter = false,
                topLeft = Offset(width / 2, width / 2),
                size = Size(this.size.width - width, this.size.height - width),
                style = Stroke(width = width, cap = StrokeCap.Round),
            )
        }
    }
}

/** The accent colour of a camera status: icons, banner accents and guide lines, never text. */
@Composable
@ReadOnlyComposable
internal fun CcStatus.cameraAccent(): Color {
    val camera = CcTheme.colors.camera
    return when (this) {
        CcStatus.Neutral -> camera.guideNeutral
        CcStatus.Ready -> camera.guideReady
        CcStatus.Warning -> camera.guideWarning
        CcStatus.Problem -> camera.guideProblem
    }
}

/** The icon of a camera status; null means the progress ring (waiting). */
internal fun CcStatus.icon(): ImageVector? = when (this) {
    CcStatus.Neutral -> null
    CcStatus.Ready -> CcIcons.CheckCircle
    CcStatus.Warning -> CcIcons.Warning
    CcStatus.Problem -> CcIcons.CrossCircle
}
