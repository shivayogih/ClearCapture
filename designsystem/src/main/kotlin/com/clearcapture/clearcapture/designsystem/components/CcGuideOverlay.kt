package com.clearcapture.clearcapture.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.clearcapture.clearcapture.designsystem.theme.CcTheme

/** Width to height of the head-and-shoulders outline. */
private const val PORTRAIT_ASPECT = 0.8f

/** Width to height of the vehicle outline. */
private const val VEHICLE_ASPECT = 2.5f

/** Share of the guide rectangle the silhouette may fill, so it does not touch the brackets. */
private const val SILHOUETTE_FILL = 0.86f

/**
 * The framing guide over the camera preview. [guide] is given as fractions of this composable's
 * size, so the guide keeps its place in portrait, landscape and any aspect ratio; silhouettes keep
 * their own proportions inside it. Lines are 3 dp with a 1 dp black outline on each side, so they
 * stay visible over bright and dark scenes. The colour follows [status] without changing size or
 * position. Decorative: the state is conveyed by the hint banner.
 *
 * @param shape Which guide to draw; [CcGuideShape.NONE] draws nothing.
 * @param status Colour of the guide.
 * @param guide The guide rectangle in fractions of the overlay.
 * @param modifier Layout of the overlay; usually the size of the preview.
 */
@Composable
public fun CcGuideOverlay(
    shape: CcGuideShape,
    status: CcStatus,
    guide: CcGuideRect,
    modifier: Modifier = Modifier,
) {
    val sizes = CcTheme.sizes
    val outlineColor = CcTheme.colors.camera.guideOutline
    val color by animateColorAsState(
        targetValue = status.cameraAccent(),
        animationSpec = tween(CcTheme.motion.standardMillis),
        label = "CcGuideColor",
    )
    Canvas(modifier = modifier.clearAndSetSemantics {}) {
        if (shape == CcGuideShape.NONE) return@Canvas
        val rect = Rect(
            left = guide.left * size.width,
            top = guide.top * size.height,
            right = guide.right * size.width,
            bottom = guide.bottom * size.height,
        )
        val path = Path()
        addBrackets(path, rect, sizes.guideBracket.toPx())
        when (shape) {
            CcGuideShape.PORTRAIT -> addPortrait(path, fit(rect, PORTRAIT_ASPECT))
            CcGuideShape.VEHICLE -> addVehicle(path, fit(rect, VEHICLE_ASPECT))
            CcGuideShape.CONTAINER, CcGuideShape.NONE -> Unit
        }
        val stroke = sizes.guideStroke.toPx()
        drawOutlined(path, color, outlineColor, stroke, stroke + 2 * sizes.guideOutline.toPx())
    }
}

private fun DrawScope.drawOutlined(path: Path, color: Color, outline: Color, width: Float, outlineWidth: Float) {
    drawPath(path, outline, style = Stroke(width = outlineWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** The largest rectangle of [aspect] (width / height) centred in [bounds], shrunk by [SILHOUETTE_FILL]. */
private fun fit(bounds: Rect, aspect: Float): Rect {
    val maxWidth = bounds.width * SILHOUETTE_FILL
    val maxHeight = bounds.height * SILHOUETTE_FILL
    val width = minOf(maxWidth, maxHeight * aspect)
    val height = width / aspect
    return Rect(Offset(bounds.center.x - width / 2, bounds.center.y - height / 2), Size(width, height))
}

private fun addBrackets(path: Path, rect: Rect, bracket: Float) {
    val length = minOf(bracket, rect.width / 2, rect.height / 2)
    corner(path, rect.topLeft, length, 1f, 1f)
    corner(path, rect.topRight, length, -1f, 1f)
    corner(path, rect.bottomLeft, length, 1f, -1f)
    corner(path, rect.bottomRight, length, -1f, -1f)
}

private fun corner(path: Path, at: Offset, length: Float, dx: Float, dy: Float) {
    path.moveTo(at.x, at.y + dy * length)
    path.lineTo(at.x, at.y)
    path.lineTo(at.x + dx * length, at.y)
}

/** Head and shoulders in a box of [PORTRAIT_ASPECT]. */
private fun addPortrait(path: Path, box: Rect) {
    fun x(f: Float) = box.left + f * box.width
    fun y(f: Float) = box.top + f * box.height
    path.addOval(Rect(left = x(0.26f), top = y(0.04f), right = x(0.74f), bottom = y(0.62f)))
    path.moveTo(x(0.02f), y(1f))
    path.cubicTo(x(0.02f), y(0.80f), x(0.26f), y(0.70f), x(0.5f), y(0.70f))
    path.cubicTo(x(0.74f), y(0.70f), x(0.98f), y(0.80f), x(0.98f), y(1f))
}

/** A side-on car in a box of [VEHICLE_ASPECT]: body outline, two wheels and the ground line. */
private fun addVehicle(path: Path, box: Rect) {
    fun x(f: Float) = box.left + f * box.width
    fun y(f: Float) = box.top + f * box.height
    val wheelRadius = box.height * 0.17f
    val wheelY = y(0.80f)
    val frontWheelX = x(0.21f)
    val rearWheelX = x(0.79f)
    val gap = box.height * 0.04f
    path.moveTo(x(0.02f), y(0.80f))
    path.lineTo(x(0.02f), y(0.55f))
    path.lineTo(x(0.10f), y(0.46f))
    path.lineTo(x(0.27f), y(0.42f))
    path.lineTo(x(0.38f), y(0.12f))
    path.lineTo(x(0.70f), y(0.12f))
    path.lineTo(x(0.83f), y(0.42f))
    path.lineTo(x(0.96f), y(0.48f))
    path.lineTo(x(0.98f), y(0.80f))
    path.lineTo(rearWheelX + wheelRadius + gap, wheelY)
    path.moveTo(rearWheelX - wheelRadius - gap, wheelY)
    path.lineTo(frontWheelX + wheelRadius + gap, wheelY)
    path.moveTo(frontWheelX - wheelRadius - gap, wheelY)
    path.lineTo(x(0.02f), wheelY)
    path.addOval(Rect(Offset(frontWheelX, wheelY), wheelRadius))
    path.addOval(Rect(Offset(rearWheelX, wheelY), wheelRadius))
}
