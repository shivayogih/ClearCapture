package com.clearcapture.clearcapture.designsystem.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.clearcapture.clearcapture.designsystem.R
import com.clearcapture.clearcapture.designsystem.components.CcCheckRow
import com.clearcapture.clearcapture.designsystem.components.CcCheckStatus
import com.clearcapture.clearcapture.designsystem.components.CcGuideOverlay
import com.clearcapture.clearcapture.designsystem.components.CcGuideRect
import com.clearcapture.clearcapture.designsystem.components.CcGuideShape
import com.clearcapture.clearcapture.designsystem.components.CcHintBanner
import com.clearcapture.clearcapture.designsystem.components.CcIconButtonContent
import com.clearcapture.clearcapture.designsystem.components.CcIconButtonStyle
import com.clearcapture.clearcapture.designsystem.components.CcLocationChip
import com.clearcapture.clearcapture.designsystem.components.CcLocationState
import com.clearcapture.clearcapture.designsystem.components.CcPermissionCard
import com.clearcapture.clearcapture.designsystem.components.CcPermissionState
import com.clearcapture.clearcapture.designsystem.components.CcPrimaryButton
import com.clearcapture.clearcapture.designsystem.components.CcProfileCardContent
import com.clearcapture.clearcapture.designsystem.components.CcScrim
import com.clearcapture.clearcapture.designsystem.components.CcSecondaryButton
import com.clearcapture.clearcapture.designsystem.components.CcShutterButtonContent
import com.clearcapture.clearcapture.designsystem.components.CcShutterState
import com.clearcapture.clearcapture.designsystem.components.CcStatus
import com.clearcapture.clearcapture.designsystem.components.CcStatusKind
import com.clearcapture.clearcapture.designsystem.components.CcStatusScreen
import com.clearcapture.clearcapture.designsystem.components.CcTextFieldContent
import com.clearcapture.clearcapture.designsystem.components.CcVerdict
import com.clearcapture.clearcapture.designsystem.components.CcVerdictBadge
import com.clearcapture.clearcapture.designsystem.icons.CcIcons
import com.clearcapture.clearcapture.designsystem.theme.CcTheme

/**
 * One component in one state, shared by the IDE previews and the screenshot tests so both cover
 * exactly the same states. The texts are sample content, not SDK strings.
 */
internal class CcSample(
    val component: String,
    val state: String,
    val onCamera: Boolean,
    val content: @Composable () -> Unit,
)

/** A stand-in for the camera preview: the worst case (white) on the left, a dark scene on the right. */
@Composable
internal fun CameraBackdrop(modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(BrightScene))
        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(DarkScene))
    }
}

/** Draws [sample] on the camera backdrop or on the surface, with a margin. */
@Composable
internal fun CcSampleFrame(sample: CcSample, modifier: Modifier = Modifier) {
    if (sample.onCamera) {
        Box(modifier = modifier) {
            CameraBackdrop(modifier = Modifier.matchParentSize())
            Box(modifier = Modifier.padding(16.dp)) { sample.content() }
        }
    } else {
        Box(modifier = modifier.background(CcTheme.colors.surface).padding(16.dp)) { sample.content() }
    }
}

private val BrightScene = Color(0xFFFFFFFF)
private val DarkScene = Color(0xFF3A4046)
private val GuideRect = CcGuideRect(left = 0.15f, top = 0.12f, right = 0.85f, bottom = 0.88f)
private val NoOp: () -> Unit = {}

private fun camera(component: String, state: String, content: @Composable () -> Unit) =
    CcSample(component, state, onCamera = true, content = content)

private fun surface(component: String, state: String, content: @Composable () -> Unit) =
    CcSample(component, state, onCamera = false, content = content)

private fun shutterSamples() = listOf(
    camera("CcShutterButton", "enabled") {
        CcShutterButtonContent(CcShutterState.Enabled, NoOp, Modifier, null, forcePressed = false)
    },
    camera("CcShutterButton", "pressed") {
        CcShutterButtonContent(CcShutterState.Enabled, NoOp, Modifier, null, forcePressed = true)
    },
    camera("CcShutterButton", "disabled") {
        CcShutterButtonContent(
            CcShutterState.Disabled,
            NoOp,
            Modifier,
            stringResource(R.string.cc_hint_fit_container),
            forcePressed = false,
        )
    },
    camera("CcShutterButton", "capturing") {
        CcShutterButtonContent(CcShutterState.Capturing, NoOp, Modifier, null, forcePressed = false)
    },
)

private fun hintSamples() = listOf(
    camera("CcHintBanner", "neutral") {
        CcHintBanner(stringResource(R.string.cc_waiting_for_location), CcStatus.Neutral)
    },
    camera("CcHintBanner", "ready") { CcHintBanner(stringResource(R.string.cc_status_ready), CcStatus.Ready) },
    camera("CcHintBanner", "warning") { CcHintBanner(stringResource(R.string.cc_hint_too_dark), CcStatus.Warning) },
    camera("CcHintBanner", "problem") {
        CcHintBanner(stringResource(R.string.cc_hint_fit_container), CcStatus.Problem)
    },
)

private fun guideSamples(): List<CcSample> {
    val shaped = listOf(CcGuideShape.CONTAINER, CcGuideShape.PORTRAIT, CcGuideShape.VEHICLE).flatMap { shape ->
        CcStatus.entries.map { status ->
            CcSample("CcGuideOverlay", "${shape.name.lowercase()}_${status.name.lowercase()}", onCamera = false) {
                GuideSample(shape, status)
            }
        }
    }
    val none = CcSample("CcGuideOverlay", "none", onCamera = false) { GuideSample(CcGuideShape.NONE, CcStatus.Ready) }
    return shaped + none
}

/** The guide over a backdrop of a landscape (4:3) preview. */
@Composable
private fun GuideSample(shape: CcGuideShape, status: CcStatus) {
    Box(modifier = Modifier.width(320.dp).height(240.dp)) {
        CameraBackdrop(modifier = Modifier.fillMaxSize())
        CcGuideOverlay(shape = shape, status = status, guide = GuideRect, modifier = Modifier.fillMaxSize())
    }
}

private fun chipSamples() = listOf(
    camera("CcLocationChip", "acquiring") { CcLocationChip(CcLocationState.Acquiring) },
    camera("CcLocationChip", "ready") { CcLocationChip(CcLocationState.Ready(accuracyMeters = 4)) },
    camera("CcLocationChip", "weak") { CcLocationChip(CcLocationState.Weak(accuracyMeters = 40)) },
    camera("CcLocationChip", "off") { CcLocationChip(CcLocationState.Off, onFix = NoOp) },
    camera("CcLocationChip", "no_permission") { CcLocationChip(CcLocationState.NoPermission, onFix = NoOp) },
)

private fun iconButtonSamples(): List<CcSample> = CcIconButtonStyle.entries.flatMap { style ->
    val prefix = style.name.lowercase()
    val make: (String, @Composable () -> Unit) -> CcSample = { state, content ->
        CcSample("CcIconButton", "${prefix}_$state", onCamera = style == CcIconButtonStyle.Camera, content = content)
    }
    val icon = if (style == CcIconButtonStyle.Camera) CcIcons.Close else CcIcons.Back
    val label = if (style == CcIconButtonStyle.Camera) R.string.cc_close else R.string.cc_back
    listOf(
        make("default") {
            CcIconButtonContent(icon, stringResource(label), NoOp, Modifier, true, null, style, forcePressed = false)
        },
        make("pressed") {
            CcIconButtonContent(icon, stringResource(label), NoOp, Modifier, true, null, style, forcePressed = true)
        },
        make("toggled") {
            CcIconButtonContent(
                CcIcons.TorchOn, stringResource(R.string.cc_torch), NoOp, Modifier, true, true, style, false,
            )
        },
        make("disabled") {
            CcIconButtonContent(icon, stringResource(label), NoOp, Modifier, false, null, style, forcePressed = false)
        },
    )
}

private fun profileSamples(): List<CcSample> {
    @Composable
    fun card(selected: Boolean, pressed: Boolean) = CcProfileCardContent(
        title = "Shipping container",
        description = "Doors, seals and markings",
        selected = selected,
        onClick = NoOp,
        modifier = Modifier,
        icon = CcIcons.Container,
        forcePressed = pressed,
    )
    return listOf(
        surface("CcProfileCard", "default") { card(selected = false, pressed = false) },
        surface("CcProfileCard", "pressed") { card(selected = false, pressed = true) },
        surface("CcProfileCard", "selected") { card(selected = true, pressed = false) },
    )
}

private fun verdictSamples() = CcVerdict.entries.map { verdict ->
    surface("CcVerdictBadge", verdict.name.lowercase()) { CcVerdictBadge(verdict) }
}

private fun checkRowSamples() = listOf(
    surface("CcCheckRow", "pass") { CcCheckRow("Sharpness", CcCheckStatus.Pass, reason = "Clear") },
    surface("CcCheckRow", "warning") {
        CcCheckRow(
            "Glare",
            CcCheckStatus.Warning,
            reason = "Reflection on part of the subject",
            advice = "Change your angle if you can",
        )
    },
    surface("CcCheckRow", "fail") {
        CcCheckRow(
            "Sharpness",
            CcCheckStatus.Fail,
            reason = stringResource(R.string.cc_reason_blurred),
        )
    },
)

private fun buttonSamples() = listOf(
    surface("CcPrimaryButton", "enabled") { CcPrimaryButton("Accept", NoOp) },
    surface("CcPrimaryButton", "disabled") { CcPrimaryButton("Accept", NoOp, enabled = false) },
    surface("CcPrimaryButton", "loading") { CcPrimaryButton("Accept", NoOp, loading = true) },
    surface("CcSecondaryButton", "enabled") { CcSecondaryButton("Retake", NoOp) },
    surface("CcSecondaryButton", "disabled") { CcSecondaryButton("Retake", NoOp, enabled = false) },
)

private fun permissionSamples() = CcPermissionState.entries.map { state ->
    surface("CcPermissionCard", state.name.lowercase()) {
        CcPermissionCard(
            title = "Camera",
            reason = "Needed to take the photo of the container.",
            state = state,
            onAction = NoOp,
            icon = CcIcons.Camera,
        )
    }
}

private fun statusScreenSamples() = listOf(
    CcSample("CcStatusScreen", "info", onCamera = false) {
        Box(modifier = Modifier.fillMaxWidth().height(520.dp)) {
            CcStatusScreen(
                kind = CcStatusKind.Info,
                title = "Location is off",
                message = "Turn on location to take photos that can be accepted.",
                actionLabel = stringResource(R.string.cc_open_settings),
                onAction = NoOp,
            )
        }
    },
    CcSample("CcStatusScreen", "error", onCamera = false) {
        Box(modifier = Modifier.fillMaxWidth().height(520.dp)) {
            CcStatusScreen(
                kind = CcStatusKind.Error,
                title = "Camera unavailable",
                message = "Another app is using the camera. Close it and try again.",
                actionLabel = "Try again",
                onAction = NoOp,
            )
        }
    },
)

private fun textFieldSamples(): List<CcSample> {
    @Composable
    fun field(value: String, error: String?, focused: Boolean) = CcTextFieldContent(
        value = value,
        onValueChange = {},
        label = "Reason for accepting",
        maxLength = 200,
        modifier = Modifier,
        errorMessage = error,
        enabled = true,
        forceFocused = focused,
    )
    return listOf(
        surface("CcTextField", "default") { field("", error = null, focused = false) },
        surface("CcTextField", "focused") { field("Customer asked for this angle", error = null, focused = true) },
        surface("CcTextField", "error") { field("Ok", error = "Write at least 10 characters", focused = false) },
    )
}

private fun scrimSamples() = listOf(
    camera("CcScrim", "default") {
        CcScrim(modifier = Modifier.width(280.dp).height(80.dp), shape = CcTheme.shapes.pill)
    },
)

/** Every component of DESIGN_SYSTEM.md section 6 in every state. */
internal val CcSamples: List<CcSample> by lazy {
    shutterSamples() + hintSamples() + guideSamples() + chipSamples() + iconButtonSamples() +
        profileSamples() + verdictSamples() + checkRowSamples() + buttonSamples() + permissionSamples() +
        statusScreenSamples() + textFieldSamples() + scrimSamples()
}

/** The samples of one component. */
internal fun samplesOf(component: String): List<CcSample> = CcSamples.filter { it.component == component }

