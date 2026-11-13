package com.clearcapture.clearcapture.ui.camera

import com.clearcapture.clearcapture.domain.hint.HintKind
import com.clearcapture.clearcapture.domain.hint.LiveGuidance
import com.clearcapture.clearcapture.domain.model.CameraCapabilities
import com.clearcapture.clearcapture.domain.model.CaptureError
import com.clearcapture.clearcapture.domain.model.CaptureProfile
import com.clearcapture.clearcapture.domain.model.LocationStatus
import com.clearcapture.clearcapture.domain.model.ProfileResolver
import kotlin.math.roundToInt

/** Something that changes the camera screen's state. */
sealed interface CameraChange {
    data class ProfileLoaded(val profile: CaptureProfile) : CameraChange
    data class CameraReady(val capabilities: CameraCapabilities, val analysisAvailable: Boolean) : CameraChange
    data class CameraFailed(val error: CaptureError) : CameraChange
    data class Guidance(val guidance: LiveGuidance) : CameraChange
    data object CaptureStarted : CameraChange
    data object CaptureFinished : CameraChange
    data class TorchChanged(val on: Boolean) : CameraChange
}

/** Pure state transitions of the camera screen (LLD section 4.1 table), including the shutter gating rule. */
object CameraReducer {

    fun reduce(state: CameraUiState, change: CameraChange): CameraUiState = when (change) {
        is CameraChange.ProfileLoaded -> state.copy(profile = change.profile)
        is CameraChange.CameraReady -> state.copy(
            camera = CameraStatus.Ready,
            torchAvailable = change.capabilities.hasFlash,
            focusAvailable = change.capabilities.hasAutoFocus,
            analyzerAvailable = change.analysisAvailable,
            profile = state.profile?.let { ProfileResolver.resolve(it, change.capabilities.traits) },
        )
        is CameraChange.CameraFailed -> state.copy(camera = CameraStatus.Error(change.error), torchOn = false)
        is CameraChange.Guidance -> state.copy(
            hint = change.guidance.hint.kind,
            guideStatus = change.guidance.guideStatus,
            location = locationChip(change.guidance.location, state.profile),
            liveBlock = when {
                change.guidance.captureEnabled -> null
                change.guidance.hint.kind == HintKind.LOCATION_BLOCKED -> ShutterBlock.LOCATION
                else -> ShutterBlock.QUALITY
            },
        )
        CameraChange.CaptureStarted -> state.copy(isCapturing = true)
        CameraChange.CaptureFinished -> state.copy(isCapturing = false)
        is CameraChange.TorchChanged -> state.copy(torchOn = change.on)
    }.gated()

    /** Shutter gating rule (LLD 3.4): (a) camera not ready, (b) capturing, then the live block for (c) and (d). */
    private fun CameraUiState.gated(): CameraUiState {
        val reason = when {
            camera != CameraStatus.Ready -> ShutterBlock.CAMERA_NOT_READY
            isCapturing -> ShutterBlock.CAPTURING
            else -> liveBlock
        }
        return copy(captureEnabled = reason == null, captureDisabledReason = reason)
    }

    private fun locationChip(status: LocationStatus, profile: CaptureProfile?): LocationChipUi = when (status) {
        LocationStatus.NotRequired -> LocationChipUi.NotRequired
        LocationStatus.PermissionMissing -> LocationChipUi.NoPermission
        LocationStatus.ProviderDisabled -> LocationChipUi.Off
        LocationStatus.Acquiring -> if (profile?.location?.required == false) LocationChipUi.NotRequired else LocationChipUi.Acquiring
        is LocationStatus.Ready -> LocationChipUi.Ready(status.fix.accuracyMeters.roundToInt())
        is LocationStatus.Stale -> LocationChipUi.Weak(status.fix.accuracyMeters.roundToInt())
        is LocationStatus.Inaccurate -> LocationChipUi.Weak(status.fix.accuracyMeters.roundToInt())
    }
}
