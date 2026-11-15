package com.clearcapture.clearcapture.ui.session

import com.clearcapture.clearcapture.domain.model.CaptureError
import com.clearcapture.clearcapture.domain.model.FlowOutcome
import com.clearcapture.clearcapture.domain.model.GuideShape
import com.clearcapture.clearcapture.domain.model.Verdict
import com.clearcapture.clearcapture.ui.camera.CameraUiState
import com.clearcapture.clearcapture.ui.permission.PermissionKind
import com.clearcapture.clearcapture.ui.permission.PermissionUiState
import com.clearcapture.clearcapture.ui.review.ReviewUiState

/** A subject the worker can switch to on the capture screen. */
data class SubjectOptionUi(val profileId: String, val titleKey: String, val guideShape: GuideShape)

/** A photograph already kept in this session, shown in the thumbnail strip. */
data class KeptPhotoUi(val captureId: String, val uri: String, val verdict: Verdict)

/** Something the worker must know now (SDK_REQUIREMENTS 7.1c). Shown as a panel on the capture screen. */
sealed interface SessionAlert {
    /** Free space is below the hard limit: the shutter is disabled until space is freed. */
    data object StorageFull : SessionAlert

    /** The kept photo could not be stored anywhere; it is still safe in pending storage and can be retried. */
    data class StoreFailed(val reason: String, val outOfSpace: Boolean) : SessionAlert

    /** The host's destination failed, so the photo was kept in SDK storage instead. */
    data class StoredInFallback(val reason: String) : SessionAlert

    /** Taking or checking the photo failed. */
    data class CaptureFailed(val error: CaptureError) : SessionAlert
}

/** State of the single capture screen (SDK_REQUIREMENTS 7.0). */
data class CaptureSessionUiState(
    val loading: Boolean = true,
    val permission: PermissionUiState = PermissionUiState(),
    val camera: CameraUiState = CameraUiState(),
    /** The subject of the next photo. */
    val subjectId: String? = null,
    /** The host's label for the next photo, for example "Bin front". */
    val label: String? = null,
    val subjects: List<SubjectOptionUi> = emptyList(),
    val canChangeSubject: Boolean = false,
    val kept: List<KeptPhotoUi> = emptyList(),
    val minPhotos: Int = 1,
    val maxPhotos: Int = 1,
    /** The verdict panel for a photo with a warning or failure, waiting for Keep or Retake. */
    val panel: ReviewUiState? = null,
    val analysing: Boolean = false,
    val alert: SessionAlert? = null,
    /** Free space is getting low; photos may not save. */
    val storageLow: Boolean = false,
) {
    /** True when everything needed is granted and the camera can be shown. */
    val ready: Boolean get() = permission.step == com.clearcapture.clearcapture.ui.permission.PermissionStep.Done

    /** The number of the next photo, 1-based. */
    val photoNumber: Int get() = kept.size + 1

    /** True once enough photos are kept to finish. */
    val canFinish: Boolean get() = kept.size >= minPhotos && panel == null && !analysing

    /** True when the shutter may be pressed: the camera's own gating plus the session's. */
    val shutterEnabled: Boolean
        get() = camera.captureEnabled && kept.size < maxPhotos && panel == null && !analysing && alert !is SessionAlert.StorageFull
}

/** What the worker or the system did. */
sealed interface SessionIntent {
    /** A readiness permission intent. */
    data class Permission(val intent: com.clearcapture.clearcapture.ui.permission.PermissionIntent) : SessionIntent
    data object Shutter : SessionIntent
    data class TapToFocus(val x: Float, val y: Float) : SessionIntent
    data object ToggleTorch : SessionIntent
    data object OpenLocationSettings : SessionIntent
    data class SelectSubject(val profileId: String) : SessionIntent

    /** Keep the photo in the verdict panel. */
    data object Keep : SessionIntent

    /** Discard the photo in the verdict panel and take another. */
    data object Retake : SessionIntent
    data class OverrideReasonChanged(val text: String) : SessionIntent

    /** Remove an already kept photo before finishing. */
    data class RemovePhoto(val captureId: String) : SessionIntent

    /** Finish with the photos kept (allowed once the minimum is reached). */
    data object Finish : SessionIntent

    /** Leave the session; kept photos are returned with Cancelled. */
    data object Leave : SessionIntent
    data object DismissAlert : SessionIntent

    /** Try again to store a photo whose storing failed. */
    data object RetryStore : SessionIntent

    /** The screen came back to the foreground: recheck permissions, location services and storage. */
    data object Resume : SessionIntent
}

/** One-off effects. */
sealed interface SessionEffect {
    data class LaunchPermissions(val kinds: Set<PermissionKind>, val permissions: List<String>) : SessionEffect
    data object OpenAppSettings : SessionEffect
    data object OpenLocationSettings : SessionEffect
    data object HapticShutter : SessionEffect
    data class HapticVerdict(val verdict: Verdict) : SessionEffect

    /** A photo was kept; brief confirmation. */
    data class PhotoKept(val number: Int) : SessionEffect

    /** End the session with [outcome]; `:capture:api` maps it to the host's result. */
    data class Finish(val outcome: FlowOutcome) : SessionEffect
}
