package com.clearcapture.clearcapture.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.clearcapture.clearcapture.designsystem.components.CcGuideOverlay
import com.clearcapture.clearcapture.designsystem.components.CcGuideRect
import com.clearcapture.clearcapture.designsystem.components.CcGuideShape
import com.clearcapture.clearcapture.designsystem.components.CcHintBanner
import com.clearcapture.clearcapture.designsystem.components.CcIconButton
import com.clearcapture.clearcapture.designsystem.components.CcLocationChip
import com.clearcapture.clearcapture.designsystem.components.CcLocationState
import com.clearcapture.clearcapture.designsystem.components.CcShutterButton
import com.clearcapture.clearcapture.designsystem.components.CcShutterState
import com.clearcapture.clearcapture.designsystem.components.CcStatus
import com.clearcapture.clearcapture.designsystem.components.CcStatusKind
import com.clearcapture.clearcapture.designsystem.components.CcStatusScreen
import com.clearcapture.clearcapture.designsystem.icons.CcIcons
import com.clearcapture.clearcapture.designsystem.theme.CcTheme
import com.clearcapture.clearcapture.domain.hint.GuideStatus
import com.clearcapture.clearcapture.domain.model.CaptureError
import com.clearcapture.clearcapture.domain.model.GuideShape
import com.clearcapture.clearcapture.ui.R
import com.clearcapture.clearcapture.ui.common.hintText
import com.clearcapture.clearcapture.ui.text.ReasonText
import com.clearcapture.clearcapture.designsystem.R as DsR

/**
 * The camera screen (DESIGN_SYSTEM 6.1). The preview fills the screen; controls sit on scrims on the thumb side:
 * shutter on the right edge in landscape, bottom centre in portrait. Stateless; [preview] is supplied by the SDK
 * so this module never touches CameraX directly.
 */
@Composable
fun CameraScreen(
    state: CameraUiState,
    onIntent: (CameraIntent) -> Unit,
    preview: @Composable (modifier: Modifier, onTap: ((Float, Float) -> Unit)?) -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    val camera = state.camera
    if (camera is CameraStatus.Error) {
        CameraErrorScreen(camera.error, onIntent, modifier)
        return
    }
    BoxWithConstraints(modifier.fillMaxSize().background(CcTheme.colors.camera.scrim)) {
        val landscape = maxWidth > maxHeight
        val onTap: ((Float, Float) -> Unit)? =
            if (state.focusAvailable) { x, y -> onIntent(CameraIntent.TapToFocus(x, y)) } else null
        preview(Modifier.fillMaxSize(), onTap)
        val shape = state.profile?.guideShape
        val guide = state.profile?.guide
        if (shape != null && shape != GuideShape.NONE && guide != null) {
            CcGuideOverlay(
                shape = shape.toCc(),
                status = state.guideStatus.toCc(),
                guide = CcGuideRect(guide.left, guide.top, guide.right, guide.bottom),
                modifier = Modifier.fillMaxSize(),
            )
        }
        CameraControls(state, onIntent, landscape, message, Modifier.fillMaxSize().safeDrawingPadding().padding(CcTheme.spacing.lg))
    }
}

@Composable
private fun CameraControls(
    state: CameraUiState,
    onIntent: (CameraIntent) -> Unit,
    landscape: Boolean,
    message: String?,
    modifier: Modifier,
) {
    val hint = hintText(state.hint, state.location, state.profile?.guideShape)
    val status = state.guideStatus.toCc()
    val reason = disabledReason(state, hint)
    Box(modifier) {
        Column(Modifier.align(Alignment.TopCenter), horizontalAlignment = Alignment.CenterHorizontally) {
            CcHintBanner(text = message ?: hint, status = if (message != null) CcStatus.Problem else status)
            if (!state.analyzerAvailable) {
                CcHintBanner(
                    text = stringResource(R.string.cc_hint_analyzer_unavailable),
                    status = CcStatus.Warning,
                    modifier = Modifier.padding(top = CcTheme.spacing.sm),
                )
            }
        }
        state.location.toCc()?.let { chip ->
            CcLocationChip(
                state = chip,
                modifier = Modifier.align(if (landscape) Alignment.TopStart else Alignment.CenterStart),
                onFix = if (chip == CcLocationState.Off || chip == CcLocationState.NoPermission) {
                    { onIntent(CameraIntent.OpenLocationSettings) }
                } else {
                    null
                },
            )
        }
        CcIconButton(
            icon = CcIcons.Close,
            contentDescription = stringResource(DsR.string.cc_close),
            onClick = { onIntent(CameraIntent.BackPressed) },
            modifier = Modifier.align(Alignment.TopEnd),
        )
        val shutter: @Composable () -> Unit = {
            CcShutterButton(
                state = when {
                    state.isCapturing -> CcShutterState.Capturing
                    state.captureEnabled -> CcShutterState.Enabled
                    else -> CcShutterState.Disabled
                },
                onClick = { onIntent(CameraIntent.ShutterPressed) },
                disabledReason = reason,
            )
        }
        val torch: @Composable () -> Unit = {
            if (state.torchAvailable) {
                CcIconButton(
                    icon = if (state.torchOn) CcIcons.TorchOn else CcIcons.TorchOff,
                    contentDescription = stringResource(DsR.string.cc_torch),
                    onClick = { onIntent(CameraIntent.ToggleTorch) },
                    toggled = state.torchOn,
                )
            }
        }
        if (landscape) {
            Column(
                Modifier.align(Alignment.CenterEnd),
                verticalArrangement = Arrangement.spacedBy(CcTheme.spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                torch()
                shutter()
            }
        } else {
            Row(Modifier.align(Alignment.BottomCenter), verticalAlignment = Alignment.CenterVertically) { shutter() }
            Box(Modifier.align(Alignment.BottomStart)) { torch() }
        }
    }
}

@Composable
private fun disabledReason(state: CameraUiState, hint: String): String? = when (state.captureDisabledReason) {
    null -> null
    ShutterBlock.CAMERA_NOT_READY -> stringResource(R.string.cc_shutter_reason_camera)
    ShutterBlock.CAPTURING -> stringResource(R.string.cc_shutter_reason_capturing)
    ShutterBlock.LOCATION, ShutterBlock.QUALITY -> hint
}

@Composable
private fun CameraErrorScreen(error: CaptureError, onIntent: (CameraIntent) -> Unit, modifier: Modifier) {
    val (title, _) = when (error) {
        is CaptureError.LocationBlocked -> ReasonText.of(error.reason)
        else -> ReasonText.of(error) to 0
    }
    CcStatusScreen(
        kind = CcStatusKind.Error,
        title = stringResource(title),
        message = stringResource(ReasonText.of(error)),
        actionLabel = stringResource(R.string.cc_action_close),
        onAction = { onIntent(CameraIntent.BackPressed) },
        modifier = modifier,
    )
}

private fun GuideShape.toCc(): CcGuideShape = when (this) {
    GuideShape.CONTAINER -> CcGuideShape.CONTAINER
    GuideShape.PORTRAIT -> CcGuideShape.PORTRAIT
    GuideShape.VEHICLE -> CcGuideShape.VEHICLE
    GuideShape.NONE -> CcGuideShape.NONE
}

private fun GuideStatus.toCc(): CcStatus = when (this) {
    GuideStatus.Neutral -> CcStatus.Neutral
    GuideStatus.Ready -> CcStatus.Ready
    GuideStatus.Warning -> CcStatus.Warning
    GuideStatus.Problem -> CcStatus.Problem
}

private fun LocationChipUi.toCc(): CcLocationState? = when (this) {
    LocationChipUi.NotRequired -> null
    LocationChipUi.Acquiring -> CcLocationState.Acquiring
    is LocationChipUi.Ready -> CcLocationState.Ready(accuracyMeters)
    is LocationChipUi.Weak -> CcLocationState.Weak(accuracyMeters)
    LocationChipUi.Off -> CcLocationState.Off
    LocationChipUi.NoPermission -> CcLocationState.NoPermission
}
