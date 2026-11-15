package com.clearcapture.clearcapture.api

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** How a capture session ended. Every outcome is one of these; the SDK never throws into the host. */
public sealed interface CaptureResult : Parcelable {

    /** The session finished with at least `minPhotos` kept photos. */
    @Parcelize
    public data class Completed(val requestId: String, val images: List<CapturedImage>) : CaptureResult

    /** The worker left before finishing. [images] are the photos already kept (often none); the host decides their fate. */
    @Parcelize
    public data class Cancelled @JvmOverloads constructor(
        val requestId: String = "",
        val images: List<CapturedImage> = emptyList(),
    ) : CaptureResult

    /**
     * The session could not run or could not finish.
     *
     * @property code one of the constants in [FailureCodes].
     * @property message a developer-facing explanation; for [FailureCodes.PERMISSION_MISSING] and
     *   [FailureCodes.NOT_READY] it lists the [Blocker] names, comma separated.
     */
    @Parcelize
    public data class Failed(val code: String, val message: String) : CaptureResult
}

/**
 * One kept photo (SDK_REQUIREMENTS 7.1b).
 *
 * @property uri where it was saved, readable by the host: `file://` for app storage and folders, `content://` for the
 *   shared gallery. The file belongs to the host from now on.
 * @property location where it was taken; `null` only when location was optional or off, with the reason in
 *   [locationNote].
 * @property sha256 hex SHA-256 of the saved bytes, so a server can detect a changed file.
 * @property gpsTime true when the time came from the GPS fix rather than only the device clock.
 * @property deliveryNote set when the requested storage failed and the photo was kept in the SDK's storage instead.
 */
@Parcelize
public data class CapturedImage(
    val captureId: String,
    val uri: Uri,
    val profileId: String,
    val label: String?,
    val quality: QualitySummary,
    val location: GeoStamp?,
    val locationNote: String,
    val capturedAtEpochMillis: Long,
    val gpsTime: Boolean,
    val sha256: String,
    val width: Int,
    val height: Int,
    val fileBytes: Long,
    val references: Map<String, String>,
    val deliveryNote: String?,
) : Parcelable

/** The values of [CaptureResult.Failed.code]. */
public object FailureCodes {
    /** Host-managed mode and a permission is missing. */
    public const val PERMISSION_MISSING: String = "PERMISSION_MISSING"

    /** A blocker that the user cannot fix in the flow (no camera, policy, unsupported ABI, ...). */
    public const val NOT_READY: String = "NOT_READY"

    /** The camera could not be opened or used. */
    public const val CAMERA_ERROR: String = "CAMERA_ERROR"

    /** Not enough space to start a session. */
    public const val STORAGE_FULL: String = "STORAGE_FULL"

    /** The requested storage destination cannot be written. */
    public const val STORAGE_UNAVAILABLE: String = "STORAGE_UNAVAILABLE"

    /** A requested subject profile is unknown. */
    public const val PROFILE_NOT_FOUND: String = "PROFILE_NOT_FOUND"

    /** An unexpected error; details are in the log. */
    public const val INTERNAL: String = "INTERNAL"
}

/** The quality verdict of a kept photo. */
@Parcelize
public data class QualitySummary(
    /** `PASS`, `WARNING` or `FAIL`. */
    val verdict: String,
    /** 0..100, for reporting only. */
    val score: Int,
    val checks: List<CheckSummary>,
    /** The reason the worker gave when keeping a failed photo, if the profile allowed it. */
    val overriddenReason: String? = null,
) : Parcelable

/** The result of one check. */
@Parcelize
public data class CheckSummary(
    /** `BLUR`, `EXPOSURE`, `GLARE`, `FRAMING`, `RESOLUTION`, `OBSTRUCTION` or `LOCATION`. */
    val check: String,
    /** `PASS`, `WARNING` or `FAIL`. */
    val verdict: String,
    /** Why the check did not pass, or `null`. */
    val reasonCode: String?,
    /** The measured values. */
    val metrics: Map<String, Double>,
) : Parcelable

/** The location written into the photo. */
@Parcelize
public data class GeoStamp(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val accuracyMeters: Float,
    val fixTimeEpochMillis: Long,
    val provider: String,
    /** True when the fix came from a mock-location provider. */
    val isMock: Boolean,
) : Parcelable
