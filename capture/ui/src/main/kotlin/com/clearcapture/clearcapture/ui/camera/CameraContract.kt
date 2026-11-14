package com.clearcapture.clearcapture.ui.camera

import com.clearcapture.clearcapture.domain.hint.GuideStatus
import com.clearcapture.clearcapture.domain.hint.HintKind
import com.clearcapture.clearcapture.domain.model.CaptureError
import com.clearcapture.clearcapture.domain.model.CaptureProfile

/** Camera lifecycle as the screen shows it. */
sealed interface CameraStatus {
    /** Binding in progress. */
    data object Starting : CameraStatus

    /** Showing frames. */
    data object Ready : CameraStatus

    /** The camera could not be used. */
    data class Error(val error: CaptureError) : CameraStatus
}

/** The location chip's state. */
sealed interface LocationChipUi {
    data object NotRequired : LocationChipUi
    data object Acquiring : LocationChipUi
    data class Ready(val accuracyMeters: Int) : LocationChipUi
    data class Weak(val accuracyMeters: Int) : LocationChipUi
    data object Off : LocationChipUi
    data object NoPermission : LocationChipUi
}

/** Why the shutter is disabled, shown next to it and read out by TalkBack. */
enum class ShutterBlock { CAMERA_NOT_READY, CAPTURING, LOCATION, QUALITY }

/** State of the camera screen (LLD section 4.1). */
data class CameraUiState(
    /** The profile in use, resolved with the camera's traits once the camera is ready. */
    val profile: CaptureProfile? = null,
    val camera: CameraStatus = CameraStatus.Starting,
    val hint: HintKind = HintKind.NONE,
    val guideStatus: GuideStatus = GuideStatus.Neutral,
    val location: LocationChipUi = LocationChipUi.Acquiring,
    val analyzerAvailable: Boolean = true,
    val captureEnabled: Boolean = false,
    /** Set whenever [captureEnabled] is false. */
    val captureDisabledReason: ShutterBlock? = ShutterBlock.CAMERA_NOT_READY,
    val isCapturing: Boolean = false,
    /** What live guidance says about the shutter (gating rules c and d); 
ull when it allows capture. */
    val liveBlock: ShutterBlock? = null,
    val torchOn: Boolean = false,
    val torchAvailable: Boolean = false,
    val focusAvailable: Boolean = false,
)

/** Intents of the camera screen. */
sealed interface CameraIntent {
    data class Start(val profileId: String) : CameraIntent
    data object ShutterPressed : CameraIntent
    data class TapToFocus(val x: Float, val y: Float) : CameraIntent
    data object ToggleTorch : CameraIntent
    data object BackPressed : CameraIntent
    data object OpenLocationSettings : CameraIntent
}

/** Effects of the camera screen. */
sealed interface CameraEffect {
    data class NavigateToReview(val captureId: String) : CameraEffect
    data object NavigateBack : CameraEffect
    data object OpenLocationSettings : CameraEffect
    data object HapticShutter : CameraEffect

    /** A capture failed; the screen shows the message and action for [error] (LLD section 11). */
    data class ShowError(val error: CaptureError) : CameraEffect
}
