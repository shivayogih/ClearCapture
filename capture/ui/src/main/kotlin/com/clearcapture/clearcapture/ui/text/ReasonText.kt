package com.clearcapture.clearcapture.ui.text

import androidx.annotation.StringRes
import com.clearcapture.clearcapture.domain.model.CaptureError
import com.clearcapture.clearcapture.domain.model.ReasonCode
import com.clearcapture.clearcapture.ui.R

/** The title and advice shown for a reason code. Exhaustive, so a new reason code cannot ship without text. */
object ReasonText {

    /** String resources for [reason]: title, then advice. */
    fun of(reason: ReasonCode): Pair<Int, Int> = when (reason) {
        ReasonCode.BLUR_MOTION -> R.string.cc_reason_blur_motion_title to R.string.cc_reason_blur_motion_advice
        ReasonCode.BLUR_FOCUS -> R.string.cc_reason_blur_focus_title to R.string.cc_reason_blur_focus_advice
        ReasonCode.TOO_DARK -> R.string.cc_reason_too_dark_title to R.string.cc_reason_too_dark_advice
        ReasonCode.TOO_BRIGHT -> R.string.cc_reason_too_bright_title to R.string.cc_reason_too_bright_advice
        ReasonCode.GLARE_ON_SUBJECT -> R.string.cc_reason_glare_on_subject_title to R.string.cc_reason_glare_on_subject_advice
        ReasonCode.SUBJECT_CUT_OFF -> R.string.cc_reason_subject_cut_off_title to R.string.cc_reason_subject_cut_off_advice
        ReasonCode.SUBJECT_TOO_SMALL -> R.string.cc_reason_subject_too_small_title to R.string.cc_reason_subject_too_small_advice
        ReasonCode.SUBJECT_TOO_CLOSE -> R.string.cc_reason_subject_too_close_title to R.string.cc_reason_subject_too_close_advice
        ReasonCode.LOW_RESOLUTION -> R.string.cc_reason_low_resolution_title to R.string.cc_reason_low_resolution_advice
        ReasonCode.FILE_CORRUPT -> R.string.cc_reason_file_corrupt_title to R.string.cc_reason_file_corrupt_advice
        ReasonCode.ORIENTATION_INVALID -> R.string.cc_reason_orientation_invalid_title to R.string.cc_reason_orientation_invalid_advice
        ReasonCode.OBSTRUCTED -> R.string.cc_reason_obstructed_title to R.string.cc_reason_obstructed_advice
        ReasonCode.LOCATION_PERMISSION -> R.string.cc_reason_location_permission_title to R.string.cc_reason_location_permission_advice
        ReasonCode.LOCATION_DISABLED -> R.string.cc_reason_location_disabled_title to R.string.cc_reason_location_disabled_advice
        ReasonCode.LOCATION_NO_FIX -> R.string.cc_reason_location_no_fix_title to R.string.cc_reason_location_no_fix_advice
        ReasonCode.LOCATION_STALE -> R.string.cc_reason_location_stale_title to R.string.cc_reason_location_stale_advice
        ReasonCode.LOCATION_INACCURATE -> R.string.cc_reason_location_inaccurate_title to R.string.cc_reason_location_inaccurate_advice
        ReasonCode.LOCATION_TAGS_MISSING ->
            R.string.cc_reason_location_tags_missing_title to R.string.cc_reason_location_tags_missing_advice
        ReasonCode.LOCATION_MOCK -> R.string.cc_reason_location_mock_title to R.string.cc_reason_location_mock_advice
        ReasonCode.ANALYSIS_UNAVAILABLE ->
            R.string.cc_reason_analysis_unavailable_title to R.string.cc_reason_analysis_unavailable_advice
    }

    /** The message for a capture error (LLD section 11). */
    @StringRes
    fun of(error: CaptureError): Int = when (error) {
        CaptureError.CameraUnavailable -> R.string.cc_error_camera_unavailable
        CaptureError.CameraInUse -> R.string.cc_error_camera_in_use
        is CaptureError.CaptureFailed -> R.string.cc_error_capture_failed
        CaptureError.StorageFull -> R.string.cc_error_storage_full
        is CaptureError.FileUnreadable -> R.string.cc_error_file_unreadable
        is CaptureError.LocationBlocked -> when (error.reason) {
            ReasonCode.LOCATION_PERMISSION -> R.string.cc_error_location_permission
            ReasonCode.LOCATION_DISABLED -> R.string.cc_error_location_disabled
            else -> R.string.cc_error_location_no_fix
        }
        CaptureError.AnalyzerUnavailable -> R.string.cc_error_analyzer_unavailable
    }
}
