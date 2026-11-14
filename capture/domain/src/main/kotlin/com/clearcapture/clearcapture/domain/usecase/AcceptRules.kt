package com.clearcapture.clearcapture.domain.usecase

import com.clearcapture.clearcapture.domain.model.Verdict

/** Whether a photograph may be accepted (LLD 4.2). Pure. */
object AcceptRules {

    /** Minimum length of the reason a user must give to accept a failed photograph. */
    const val MIN_OVERRIDE_REASON_LENGTH: Int = 10

    /** True when [verdict] may be accepted under the given rules and [overrideReason]. */
    fun canAccept(verdict: Verdict, allowAcceptOnWarning: Boolean, allowOverrideOnFail: Boolean, overrideReason: String?): Boolean =
        when (verdict) {
            Verdict.PASS -> true
            Verdict.WARNING -> allowAcceptOnWarning
            Verdict.FAIL -> allowOverrideOnFail && (overrideReason?.trim()?.length ?: 0) >= MIN_OVERRIDE_REASON_LENGTH
        }

    /** True when the review screen must ask for an override reason. */
    fun needsOverrideReason(verdict: Verdict, allowOverrideOnFail: Boolean): Boolean = verdict == Verdict.FAIL && allowOverrideOnFail
}
