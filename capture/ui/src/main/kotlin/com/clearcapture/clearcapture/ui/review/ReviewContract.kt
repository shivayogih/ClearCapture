package com.clearcapture.clearcapture.ui.review

import com.clearcapture.clearcapture.domain.model.FlowOutcome
import com.clearcapture.clearcapture.domain.model.QualityCheck
import com.clearcapture.clearcapture.domain.model.ReasonCode
import com.clearcapture.clearcapture.domain.model.Verdict

/** The overall verdict as the review screen shows it. */
sealed interface VerdictUi {
    data object Loading : VerdictUi
    data class Shown(val verdict: Verdict) : VerdictUi
}

/** One check row: the screen turns [check] and [reason] into the title and advice strings. */
data class CheckRowUi(val check: QualityCheck, val verdict: Verdict, val reason: ReasonCode?)

/** The location line under the checks. */
data class LocationSummaryUi(val accuracyMeters: Int, val isMock: Boolean)

/** Why Accept is not offered, shown in its place (DESIGN_SYSTEM 6.1: never disabled without a reason). */
enum class AcceptBlock { WARNING_NOT_ALLOWED, FAIL_NOT_ALLOWED, REASON_TOO_SHORT }

/** State of the review screen (LLD section 4.2). */
data class ReviewUiState(
    val imagePath: String? = null,
    val verdict: VerdictUi = VerdictUi.Loading,
    val checks: List<CheckRowUi> = emptyList(),
    val location: LocationSummaryUi? = null,
    val canAccept: Boolean = false,
    val acceptBlock: AcceptBlock? = null,
    val needsOverrideReason: Boolean = false,
    val overrideReason: String = "",
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
)

/** Intents of the review screen. */
sealed interface ReviewIntent {
    data class Load(val captureId: String) : ReviewIntent
    data object Accept : ReviewIntent
    data object Retake : ReviewIntent
    data class OverrideReasonChanged(val text: String) : ReviewIntent
}

/** Effects of the review screen. */
sealed interface ReviewEffect {
    /** End the flow with [outcome]; `:capture:api` maps it to the host's result. */
    data class Finish(val outcome: FlowOutcome) : ReviewEffect
    data object BackToCamera : ReviewEffect
    data class HapticVerdict(val verdict: Verdict) : ReviewEffect
}
