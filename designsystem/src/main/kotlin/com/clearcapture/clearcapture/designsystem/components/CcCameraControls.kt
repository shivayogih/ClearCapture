package com.clearcapture.clearcapture.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.clearcapture.clearcapture.designsystem.R
import com.clearcapture.clearcapture.designsystem.theme.CcTheme

private const val SHUTTER_PRESSED_SCALE = 0.88f
private val ShutterRingWidth = 4.dp
private val ShutterRingGap = 4.dp
private val FocusRingWidth = 2.dp

/**
 * The shutter: an 80 dp button with a 96 dp touch area, on a scrim so it stays visible over a
 * bright scene. It is announced as "Take photo"; when disabled, the reason is read out as its
 * state; while capturing, a progress ring turns around it and it cannot be pressed.
 *
 * @param state Enabled, disabled or capturing.
 * @param onClick Takes the photo; only called when [state] is [CcShutterState.Enabled].
 * @param modifier Layout of the 96 dp touch area.
 * @param disabledReason Why the photo cannot be taken; read out when [state] is [CcShutterState.Disabled].
 */
@Composable
public fun CcShutterButton(
    state: CcShutterState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    disabledReason: String? = null,
) {
    CcShutterButtonContent(state, onClick, modifier, disabledReason, forcePressed = false)
}

/** [CcShutterButton] with the pressed look forced, for previews and screenshots. */
@Composable
internal fun CcShutterButtonContent(
    state: CcShutterState,
    onClick: () -> Unit,
    modifier: Modifier,
    disabledReason: String?,
    forcePressed: Boolean,
) {
    val camera = CcTheme.colors.camera
    val sizes = CcTheme.sizes
    val label = stringResource(R.string.cc_shutter_label)
    val stateText = when (state) {
        CcShutterState.Enabled -> null
        CcShutterState.Disabled -> disabledReason
        CcShutterState.Capturing -> stringResource(R.string.cc_shutter_capturing)
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val discScale by animateFloatAsState(
        targetValue = if (pressed || forcePressed) SHUTTER_PRESSED_SCALE else 1f,
        animationSpec = tween(CcTheme.motion.fastMillis),
        label = "CcShutterPress",
    )
    val enabled = state == CcShutterState.Enabled
    val contentAlpha = if (enabled) 1f else DISABLED_ALPHA
    Box(
        modifier = modifier
            .size(sizes.shutterTarget)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick,
            )
            .semantics {
                contentDescription = label
                if (stateText != null) stateDescription = stateText
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(sizes.shutterTarget)) {
            val visualRadius = sizes.shutterVisual.toPx() / 2
            val ring = ShutterRingWidth.toPx()
            if (focused) {
                drawCircle(
                    color = camera.onScrim,
                    radius = size.minDimension / 2 - FocusRingWidth.toPx() / 2,
                    style = Stroke(FocusRingWidth.toPx()),
                )
            }
            drawCircle(color = camera.scrim, radius = visualRadius)
            drawCircle(
                color = camera.onScrim.copy(alpha = contentAlpha),
                radius = visualRadius - ring / 2,
                style = Stroke(ring),
            )
            val discRadius = (visualRadius - ring - ShutterRingGap.toPx()) * discScale
            drawCircle(color = camera.onScrim.copy(alpha = contentAlpha), radius = discRadius)
        }
        if (state == CcShutterState.Capturing) {
            CcProgressRing(color = camera.guideReady, size = sizes.shutterVisual, strokeWidth = ShutterRingWidth)
        }
    }
}

/**
 * A round icon button with a 56 dp touch target: torch, close, back. With [toggled] set it is a
 * switch (torch on or off) and its state is read out as "On" or "Off".
 *
 * @param icon The icon; see [com.clearcapture.clearcapture.designsystem.icons.CcIcons].
 * @param contentDescription What the button does, read by screen readers.
 * @param onClick Called on click, or with the new state toggled when [toggled] is set.
 * @param modifier Layout of the button.
 * @param enabled Whether it can be pressed.
 * @param toggled Null for a plain button; true or false for a switch.
 * @param style On the camera (on a scrim) or on a normal surface.
 */
@Composable
public fun CcIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    toggled: Boolean? = null,
    style: CcIconButtonStyle = CcIconButtonStyle.Camera,
) {
    CcIconButtonContent(icon, contentDescription, onClick, modifier, enabled, toggled, style, forcePressed = false)
}

/** [CcIconButton] with the pressed look forced, for previews and screenshots. */
@Composable
internal fun CcIconButtonContent(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    toggled: Boolean?,
    style: CcIconButtonStyle,
    forcePressed: Boolean,
) {
    val colors = iconButtonColors(style, toggled == true)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = CcTheme.shapes.circle
    val onText = stringResource(R.string.cc_state_on)
    val offText = stringResource(R.string.cc_state_off)
    val action = if (toggled == null) {
        Modifier.clickable(
            interactionSource = interaction,
            indication = ripple(),
            enabled = enabled,
            role = Role.Button,
            onClick = onClick,
        )
    } else {
        Modifier.toggleable(
            value = toggled,
            interactionSource = interaction,
            indication = ripple(),
            enabled = enabled,
            role = Role.Switch,
            onValueChange = { onClick() },
        )
    }
    val tint = colors.content.copy(alpha = if (enabled) 1f else DISABLED_ALPHA)
    Box(
        modifier = modifier
            .size(CcTheme.sizes.minTouchTarget)
            .clip(shape)
            .background(colors.container, shape)
            .then(action)
            .semantics {
                this.contentDescription = contentDescription
                if (toggled != null) stateDescription = if (toggled) onText else offText
            },
        contentAlignment = Alignment.Center,
    ) {
        if (pressed || forcePressed) {
            Box(
                modifier = Modifier
                    .size(CcTheme.sizes.minTouchTarget)
                    .background(colors.content.copy(alpha = PRESSED_OVERLAY_ALPHA), shape),
            )
        }
        CcIconImage(icon = icon, tint = tint, size = CcTheme.sizes.iconInControl)
    }
}

private class IconButtonColors(val container: Color, val content: Color)

@Composable
private fun iconButtonColors(style: CcIconButtonStyle, toggledOn: Boolean): IconButtonColors {
    val colors = CcTheme.colors
    return when (style) {
        CcIconButtonStyle.Camera -> if (toggledOn) {
            IconButtonColors(container = colors.camera.onScrim, content = colors.camera.guideOutline)
        } else {
            IconButtonColors(container = colors.camera.scrim, content = colors.camera.onScrim)
        }
        CcIconButtonStyle.Surface -> if (toggledOn) {
            IconButtonColors(container = colors.primaryContainer, content = colors.onPrimaryContainer)
        } else {
            IconButtonColors(container = Color.Transparent, content = colors.onSurface)
        }
    }
}
