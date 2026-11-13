package com.clearcapture.clearcapture.domain.model

import java.io.File

/** Errors of the capture flow. Each maps to one user message and one recovery action (LLD section 11). */
sealed interface CaptureError {
    /** No back camera, or binding failed. */
    data object CameraUnavailable : CaptureError

    /** Another app holds the camera. */
    data object CameraInUse : CaptureError

    /** `takePicture` reported an error. */
    data class CaptureFailed(val cause: String) : CaptureError

    /** Free space below the limit, or a write failed. */
    data object StorageFull : CaptureError

    /** The saved file could not be decoded. */
    data class FileUnreadable(val path: String) : CaptureError

    /** Capture is blocked by the location rules. */
    data class LocationBlocked(val reason: ReasonCode) : CaptureError

    /** OpenCV failed to load, or live analysis could not be bound. */
    data object AnalyzerUnavailable : CaptureError
}

/** Status of a capture record. */
enum class CaptureStatus { PENDING, ACCEPTED, DISCARDED, FAILED }

/** A photograph that has been taken and checked, waiting for the user to accept or retake it. */
data class PendingCapture(
    val captureId: String,
    val file: File,
    val profileId: String,
    val report: QualityReport,
    val fix: LocationFix?,
    val capturedAtEpochMillis: Long,
)

/** What the domain hands back when a capture is accepted. No `:capture:api` types. */
data class AcceptedCapture(
    val captureId: String,
    val file: File,
    val profileId: String,
    val report: QualityReport,
    val overrideReason: String?,
    val fix: LocationFix?,
    val capturedAtEpochMillis: Long,
)

/** Failure codes a flow can end with. Mapped one-to-one to `CaptureResult.Failed.code`. */
enum class FailureCode { PERMISSION_MISSING, NOT_READY, CAMERA_ERROR, STORAGE_FULL, PROFILE_NOT_FOUND, INTERNAL }

/** How a run of the flow ended. `:capture:api` maps it to the public `CaptureResult`. */
sealed interface FlowOutcome {
    /** The user accepted a photograph. */
    data class Accepted(val capture: AcceptedCapture) : FlowOutcome

    /** The user left the flow. */
    data object Cancelled : FlowOutcome

    /** The flow could not run or could not finish. */
    data class Failed(val code: FailureCode, val detail: String) : FlowOutcome
}

/** One row of the capture log: every attempt, whatever its outcome. */
data class CaptureRecord(
    val captureId: String,
    val requestId: String,
    val profileId: String,
    val status: CaptureStatus,
    val report: QualityReport?,
    val overrideReason: String?,
    val filePath: String?,
    val image: ImageInfo?,
    val fix: LocationFix?,
    val capturedAtEpochMillis: Long,
    val shutterMillis: Long,
    val device: DeviceInfo,
    val profileSchemaVersion: Int,
)

/** Device facts stored with each record for calibration. */
data class DeviceInfo(val model: String, val cameraId: String, val osVersion: Int)
