package com.clearcapture.clearcapture.ui.session

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import com.clearcapture.clearcapture.designsystem.components.CcGuideOverlay
import com.clearcapture.clearcapture.designsystem.components.CcGuideRect
import com.clearcapture.clearcapture.designsystem.components.CcGuideShape
import com.clearcapture.clearcapture.designsystem.components.CcHintBanner
import com.clearcapture.clearcapture.designsystem.components.CcIconButton
import com.clearcapture.clearcapture.designsystem.components.CcLocationChip
import com.clearcapture.clearcapture.designsystem.components.CcLocationState
import com.clearcapture.clearcapture.designsystem.components.CcPrimaryButton
import com.clearcapture.clearcapture.designsystem.components.CcScrim
import com.clearcapture.clearcapture.designsystem.components.CcSecondaryButton
import com.clearcapture.clearcapture.designsystem.components.CcShutterButton
import com.clearcapture.clearcapture.designsystem.components.CcShutterState
import com.clearcapture.clearcapture.designsystem.components.CcStatus
import com.clearcapture.clearcapture.designsystem.components.CcStatusKind
import com.clearcapture.clearcapture.designsystem.components.CcStatusScreen
import com.clearcapture.clearcapture.designsystem.icons.CcIcons
import com.clearcapture.clearcapture.designsystem.theme.CcTheme
import com.clearcapture.clearcapture.domain.hint.GuideStatus
import com.clearcapture.clearcapture.domain.model.GuideShape
import com.clearcapture.clearcapture.domain.model.Verdict
import com.clearcapture.clearcapture.ui.R
import com.clearcapture.clearcapture.ui.camera.CameraStatus
import com.clearcapture.clearcapture.ui.camera.LocationChipUi
import com.clearcapture.clearcapture.ui.camera.ShutterBlock
import com.clearcapture.clearcapture.ui.common.CcText
import com.clearcapture.clearcapture.ui.common.hintText
import com.clearcapture.clearcapture.ui.common.profileTitle
import com.clearcapture.clearcapture.ui.common.rememberPreview
import com.clearcapture.clearcapture.ui.permission.ReadinessPanel
import com.clearcapture.clearcapture.ui.review.VerdictPanel
import com.clearcapture.clearcapture.ui.text.ReasonText
import com.clearcapture.clearcapture.designsystem.R as DsR

/**
 * The one capture screen of a session (SDK_REQUIREMENTS 7.0, DESIGN_SYSTEM 6.1). Readiness first; then the preview
 * fills the screen with controls on scrims on the thumb side, the verdict panel for a photo that needs a decision,
 * and alerts. Stateless; [preview] is supplied by the SDK so this module never touches CameraX.
 */
@Composable
fun CaptureSessionScreen(
    state: CaptureSessionUiState,
    onIntent: (SessionIntent) -> Unit,
    preview: @Composable (modifier: Modifier, onTap: ((Float, Float) -> Unit)?) -> Unit,
    modifier: Modifier = Modifier,
    kept: String? = null,
) {
    val camera = state.camera.camera
    when {
        !state.ready -> ReadinessPanel(state.permission, { onIntent(SessionIntent.Permission(it)) }, modifier)
        camera is CameraStatus.Error -> CcStatusScreen(
            kind = CcStatusKind.Error,
            title = stringResource(ReasonText.of(camera.error)),
            message = stringResource(R.string.cc_session_camera_error_message),
            actionLabel = stringResource(R.string.cc_action_close),
            onAction = { onIntent(SessionIntent.Leave) },
            modifier = modifier,
        )
        else -> BoxWithConstraints(modifier.fillMaxSize().background(CcTheme.colors.camera.scrim)) {
            val landscape = maxWidth > maxHeight
            val onTap: ((Float, Float) -> Unit)? =
                if (state.camera.focusAvailable) { x, y -> onIntent(SessionIntent.TapToFocus(x, y)) } else null
            preview(Modifier.fillMaxSize(), onTap)
            Guide(state)
            Controls(state, onIntent, landscape, kept, Modifier.fillMaxSize().safeDrawingPadding().padding(CcTheme.spacing.lg))
            state.panel?.let { panel ->
                VerdictPanel(
                    state = panel,
                    onKeep = { onIntent(SessionIntent.Keep) },
                    onRetake = { onIntent(SessionIntent.Retake) },
                    onReasonChanged = { onIntent(SessionIntent.OverrideReasonChanged(it)) },
                    modifier = if (landscape) {
                        Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(PANEL_FRACTION).safeDrawingPadding()
                    } else {
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(PANEL_FRACTION).safeDrawingPadding()
                    },
                )
            }
            state.alert?.let { AlertPanel(it, state, onIntent, Modifier.align(Alignment.Center)) }
        }
    }
}

@Composable
private fun Guide(state: CaptureSessionUiState) {
    val profile = state.camera.profile ?: return
    val guide = profile.guide ?: return
    if (profile.guideShape == GuideShape.NONE) return
    CcGuideOverlay(
        shape = profile.guideShape.toCc(),
        status = state.camera.guideStatus.toCc(),
        guide = CcGuideRect(guide.left, guide.top, guide.right, guide.bottom),
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun Controls(
    state: CaptureSessionUiState,
    onIntent: (SessionIntent) -> Unit,
    landscape: Boolean,
    kept: String?,
    modifier: Modifier,
) {
    val liveHint = hintText(state.camera.hint, state.camera.location, state.camera.profile?.guideShape)
    val hint = if (state.analysing) stringResource(R.string.cc_session_checking) else liveHint
    val bannerGap = Modifier.padding(top = CcTheme.spacing.sm)
    Box(modifier) {
        Column(Modifier.align(Alignment.TopCenter), horizontalAlignment = Alignment.CenterHorizontally) {
            CcHintBanner(text = kept ?: hint, status = if (kept != null) CcStatus.Ready else state.camera.guideStatus.toCc())
            if (!state.camera.analyzerAvailable) {
                CcHintBanner(stringResource(R.string.cc_hint_analyzer_unavailable), CcStatus.Warning, bannerGap)
            }
            if (state.storageLow) {
                CcHintBanner(stringResource(R.string.cc_session_storage_low), CcStatus.Warning, bannerGap)
            }
        }
        Column(Modifier.align(Alignment.TopStart), verticalArrangement = Arrangement.spacedBy(CcTheme.spacing.sm)) {
            state.camera.location.toCc()?.let { chip ->
                CcLocationChip(
                    state = chip,
                    onFix = if (chip == CcLocationState.Off || chip == CcLocationState.NoPermission) {
                        { onIntent(SessionIntent.OpenLocationSettings) }
                    } else {
                        null
                    },
                )
            }
            ProgressLabel(state)
            if (state.canChangeSubject) SubjectSelector(state, onIntent, landscape)
        }
        CcIconButton(
            icon = CcIcons.Close,
            contentDescription = stringResource(DsR.string.cc_close),
            onClick = { onIntent(SessionIntent.Leave) },
            modifier = Modifier.align(Alignment.TopEnd),
        )
        val actions: @Composable () -> Unit = {
            if (state.camera.torchAvailable) {
                CcIconButton(
                    icon = if (state.camera.torchOn) CcIcons.TorchOn else CcIcons.TorchOff,
                    contentDescription = stringResource(DsR.string.cc_torch),
                    onClick = { onIntent(SessionIntent.ToggleTorch) },
                    toggled = state.camera.torchOn,
                )
            }
            CcShutterButton(
                state = when {
                    state.camera.isCapturing || state.analysing -> CcShutterState.Capturing
                    state.shutterEnabled -> CcShutterState.Enabled
                    else -> CcShutterState.Disabled
                },
                onClick = { onIntent(SessionIntent.Shutter) },
                disabledReason = disabledReason(state, hint),
            )
            if (state.canFinish) {
                CcPrimaryButton(
                    text = stringResource(R.string.cc_session_done, state.kept.size),
                    onClick = { onIntent(SessionIntent.Finish) },
                )
            }
        }
        if (landscape) {
            Column(
                Modifier.align(Alignment.CenterEnd),
                verticalArrangement = Arrangement.spacedBy(CcTheme.spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { actions() }
        } else {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = CcTheme.sizes.shutterTarget),
                horizontalArrangement = Arrangement.spacedBy(CcTheme.spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) { actions() }
        }
        if (state.kept.isNotEmpty()) KeptStrip(state, onIntent, Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun ProgressLabel(state: CaptureSessionUiState) {
    val number = minOf(state.photoNumber, state.maxPhotos)
    val subject = state.subjectId?.let { id -> state.subjects.firstOrNull { it.profileId == id }?.titleKey }?.let { profileTitle(it) }
    val text = buildString {
        append(stringResource(R.string.cc_session_photo_of, number, state.maxPhotos))
        if (state.minPhotos < state.maxPhotos) append(" " + stringResource(R.string.cc_session_at_least, state.minPhotos))
        (state.label ?: subject)?.let { append(" · ").append(it) }
    }
    CcScrim(shape = CcTheme.shapes.pill) {
        CcText(text, CcTheme.type.label, CcTheme.colors.camera.onScrim, Modifier.padding(CcTheme.spacing.md))
    }
}

@Composable
private fun SubjectSelector(state: CaptureSessionUiState, onIntent: (SessionIntent) -> Unit, landscape: Boolean) {
    val buttons: @Composable () -> Unit = {
        state.subjects.forEach { subject ->
            CcIconButton(
                icon = when (subject.guideShape) {
                    GuideShape.CONTAINER -> CcIcons.Container
                    GuideShape.PORTRAIT -> CcIcons.Person
                    GuideShape.VEHICLE -> CcIcons.Vehicle
                    GuideShape.NONE -> CcIcons.Camera
                },
                contentDescription = profileTitle(subject.titleKey),
                onClick = { onIntent(SessionIntent.SelectSubject(subject.profileId)) },
                toggled = subject.profileId == state.subjectId,
                enabled = state.panel == null && !state.analysing,
            )
        }
    }
    if (landscape) {
        Column(verticalArrangement = Arrangement.spacedBy(CcTheme.spacing.targetGap)) { buttons() }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(CcTheme.spacing.targetGap)) { buttons() }
    }
}

@Composable
private fun KeptStrip(state: CaptureSessionUiState, onIntent: (SessionIntent) -> Unit, modifier: Modifier) {
    CcScrim(modifier, shape = CcTheme.shapes.medium) {
        LazyRow(
            Modifier.padding(CcTheme.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(CcTheme.spacing.sm),
        ) {
            items(state.kept, key = { it.captureId }) { photo ->
                Box {
                    val bitmap by rememberPreview(photo.uri, THUMBNAIL_LONG_SIDE)
                    bitmap?.let {
                        Image(
                            bitmap = it,
                            contentDescription = stringResource(R.string.cc_session_kept_photo, state.kept.indexOf(photo) + 1),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(CcTheme.sizes.shutterTarget),
                        )
                    }
                    CcIconButton(
                        icon = if (photo.verdict == Verdict.PASS) CcIcons.Close else CcIcons.Warning,
                        contentDescription = stringResource(R.string.cc_session_remove_photo, state.kept.indexOf(photo) + 1),
                        onClick = { onIntent(SessionIntent.RemovePhoto(photo.captureId)) },
                        modifier = Modifier.align(Alignment.TopEnd),
                        enabled = state.panel == null && !state.analysing,
                    )
                }
            }
        }
    }
}

@Composable
private fun AlertPanel(alert: SessionAlert, state: CaptureSessionUiState, onIntent: (SessionIntent) -> Unit, modifier: Modifier) {
    val title = when (alert) {
        SessionAlert.StorageFull -> stringResource(R.string.cc_session_storage_full_title)
        is SessionAlert.StoreFailed -> stringResource(R.string.cc_session_store_failed_title)
        is SessionAlert.StoredInFallback -> stringResource(R.string.cc_session_fallback_title)
        is SessionAlert.CaptureFailed -> stringResource(ReasonText.of(alert.error))
    }
    val message = when (alert) {
        SessionAlert.StorageFull -> stringResource(R.string.cc_session_storage_full_message)
        is SessionAlert.StoreFailed -> stringResource(
            if (alert.outOfSpace) R.string.cc_session_store_failed_space else R.string.cc_session_store_failed_message,
            alert.reason,
        )
        is SessionAlert.StoredInFallback ->
            stringResource(R.string.cc_session_fallback_message, alert.reason.removePrefix("STORED_IN_FALLBACK: "))
        is SessionAlert.CaptureFailed -> stringResource(R.string.cc_session_capture_failed_message)
    }
    CcScrim(modifier.padding(CcTheme.spacing.xl), shape = CcTheme.shapes.large) {
        Column(Modifier.padding(CcTheme.spacing.xl), verticalArrangement = Arrangement.spacedBy(CcTheme.spacing.md)) {
            CcText(title, CcTheme.type.title, CcTheme.colors.camera.onScrim)
            CcText(message, CcTheme.type.body, CcTheme.colors.camera.onScrim)
            Row(horizontalArrangement = Arrangement.spacedBy(CcTheme.spacing.targetGap)) {
                when (alert) {
                    SessionAlert.StorageFull -> {
                        CcPrimaryButton(stringResource(R.string.cc_action_retry), { onIntent(SessionIntent.Resume) })
                        if (state.kept.isNotEmpty()) {
                            CcSecondaryButton(
                                stringResource(R.string.cc_session_finish_with, state.kept.size),
                                { onIntent(SessionIntent.Leave) },
                            )
                        }
                    }
                    is SessionAlert.StoreFailed -> {
                        CcPrimaryButton(stringResource(R.string.cc_action_retry), { onIntent(SessionIntent.RetryStore) })
                        CcSecondaryButton(stringResource(R.string.cc_review_retake), { onIntent(SessionIntent.Retake) })
                    }
                    else -> CcPrimaryButton(stringResource(R.string.cc_session_ok), { onIntent(SessionIntent.DismissAlert) })
                }
            }
        }
    }
}

@Composable
private fun disabledReason(state: CaptureSessionUiState, hint: String): String? = when {
    state.kept.size >= state.maxPhotos -> stringResource(R.string.cc_session_reason_max)
    state.panel != null -> stringResource(R.string.cc_session_reason_panel)
    state.alert is SessionAlert.StorageFull -> stringResource(R.string.cc_session_storage_full_title)
    else -> when (state.camera.captureDisabledReason) {
        null -> null
        ShutterBlock.CAMERA_NOT_READY -> stringResource(R.string.cc_shutter_reason_camera)
        ShutterBlock.CAPTURING -> stringResource(R.string.cc_shutter_reason_capturing)
        ShutterBlock.LOCATION, ShutterBlock.QUALITY -> hint
    }
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

private const val PANEL_FRACTION = 0.5f
private const val THUMBNAIL_LONG_SIDE = 256
