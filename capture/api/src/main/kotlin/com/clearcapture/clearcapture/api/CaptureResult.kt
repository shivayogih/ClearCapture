package com.clearcapture.clearcapture.api

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** How a launch of the capture flow ended. Every outcome is one of these; the SDK never throws into the host. */
public sealed interface CaptureResult : Parcelable {

    /**
     * The user accepted a photograph.
     *
     * @property imageUri a `file://` URI in the app's private storage. The file belongs to the host from now on.
     * @property location where the photograph was taken; `null` only when the profile does not require location.
     */
    @Parcelize
    public data class Accepted(
        val captureId: String,
        val imageUri: Uri,
        val profileId: String,
        val quality: QualitySummary,
        val location: GeoStamp?,
        val capturedAtEpochMillis: Long,
        val requestId: String,
    ) : CaptureResult

    /** The user left the flow. */
    @Parcelize
    public data object Cancelled : CaptureResult

    /**
     * The flow could not run or could not finish.
     *
     * @property code one of the constants in [FailureCodes].
     * @property message a developer-facing explanation; for [FailureCodes.PERMISSION_MISSING] and
     *   [FailureCodes.NOT_READY] it lists the [Blocker] names, comma separated.
     */
    @Parcelize
    public data class Failed(val code: String, val message: String) : CaptureResult
}

/** The values of [CaptureResult.Failed.code]. */
public object FailureCodes {
    /** Host-managed mode and a permission is missing. */
    public const val PERMISSION_MISSING: String = "PERMISSION_MISSING"

    /** A blocker that the user cannot fix in the flow (no camera, policy, unsupported ABI, ...). */
    public const val NOT_READY: String = "NOT_READY"

    /** The camera could not be opened or used. */
    public const val CAMERA_ERROR: String = "CAMERA_ERROR"

    /** The photograph could not be saved. */
    public const val STORAGE_FULL: String = "STORAGE_FULL"

    /** The requested profile ID is unknown. */
    public const val PROFILE_NOT_FOUND: String = "PROFILE_NOT_FOUND"

    /** An unexpected error; details are in the log. */
    public const val INTERNAL: String = "INTERNAL"
}

/** The quality verdict of an accepted photograph. */
@Parcelize
public data class QualitySummary(
    /** `PASS`, `WARNING` or `FAIL`. */
    val verdict: String,
    /** 0..100, for reporting only. */
    val score: Int,
    val checks: List<CheckSummary>,
    /** The reason the user gave when accepting a failed photograph, if the profile allowed it. */
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

/** The location written into the photograph. */
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
