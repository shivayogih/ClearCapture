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
    val label: String? = null,
    val locationNote: LocationNote = LocationNote.NONE,
)

/** What the domain hands back when a capture is accepted. No `:capture:api` types. */
data class AcceptedCapture(
    val captureId: String,
    /** The stored file when the destination is a file; `null` for a media-store or host-sink destination. */
    val file: File?,
    /** Where the photograph was stored, readable by the host: a `file://` or `content://` URI. */
    val uri: String,
    val profileId: String,
    val report: QualityReport,
    val overrideReason: String?,
    val fix: LocationFix?,
    val capturedAtEpochMillis: Long,
    val label: String? = null,
    val locationNote: LocationNote = LocationNote.NONE,
    val sha256: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val fileBytes: Long = 0,
    val gpsTime: Boolean = false,
    /** Set when the host's destination failed and the photo was kept in SDK storage instead. */
    val deliveryNote: String? = null,
)

/** Failure codes a flow can end with. Mapped one-to-one to `CaptureResult.Failed.code`. */
enum class FailureCode { PERMISSION_MISSING, NOT_READY, CAMERA_ERROR, STORAGE_FULL, STORAGE_UNAVAILABLE, PROFILE_NOT_FOUND, INTERNAL }

/** How a run of the flow ended. `:capture:api` maps it to the public `CaptureResult`. */
sealed interface FlowOutcome {
    /** The session finished with at least the minimum number of kept photographs. */
    data class Completed(val captures: List<AcceptedCapture>) : FlowOutcome

    /** The worker left early; [captures] are the photographs already kept, often none. */
    data class Cancelled(val captures: List<AcceptedCapture> = emptyList()) : FlowOutcome

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
