package com.clearcapture.clearcapture.ui.review

import com.clearcapture.clearcapture.domain.model.PendingCapture
import com.clearcapture.clearcapture.domain.model.Verdict
import com.clearcapture.clearcapture.domain.usecase.AcceptRules
import kotlin.math.roundToInt

/** The accept rules in force for this launch: the profile's, unless the host overrode them in its config. */
data class AcceptPolicy(val allowAcceptOnWarning: Boolean, val allowOverrideOnFail: Boolean)

/** Pure state transitions of the review screen. */
object ReviewReducer {

    fun loaded(state: ReviewUiState, capture: PendingCapture, policy: AcceptPolicy): ReviewUiState = state.copy(
        imagePath = capture.file.path,
        verdict = VerdictUi.Shown(capture.report.verdict),
        checks = capture.report.results.map { CheckRowUi(it.check, it.verdict, it.reason) },
        location = capture.fix?.let { LocationSummaryUi(it.accuracyMeters.roundToInt(), it.isMock) },
        needsOverrideReason = AcceptRules.needsOverrideReason(capture.report.verdict, policy.allowOverrideOnFail),
    ).withAcceptance(capture.report.verdict, policy)

    fun reasonChanged(state: ReviewUiState, text: String, verdict: Verdict, policy: AcceptPolicy): ReviewUiState =
        state.copy(overrideReason = text.take(MAX_REASON_LENGTH)).withAcceptance(verdict, policy)

    private fun ReviewUiState.withAcceptance(verdict: Verdict, policy: AcceptPolicy): ReviewUiState {
        val can = AcceptRules.canAccept(verdict, policy.allowAcceptOnWarning, policy.allowOverrideOnFail, overrideReason)
        val block = when {
            can -> null
            verdict == Verdict.WARNING -> AcceptBlock.WARNING_NOT_ALLOWED
            verdict == Verdict.FAIL && policy.allowOverrideOnFail -> AcceptBlock.REASON_TOO_SHORT
            else -> AcceptBlock.FAIL_NOT_ALLOWED
        }
        return copy(canAccept = can, acceptBlock = block)
    }

    /** Longest override reason accepted, so the record stays small. */
    const val MAX_REASON_LENGTH: Int = 200
}
