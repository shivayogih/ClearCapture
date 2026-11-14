package com.clearcapture.clearcapture.domain.quality

import com.clearcapture.clearcapture.domain.model.CaptureProfile
import com.clearcapture.clearcapture.domain.model.CheckResult
import com.clearcapture.clearcapture.domain.model.QualityReport
import com.clearcapture.clearcapture.domain.model.ReasonCode
import com.clearcapture.clearcapture.domain.model.Stage
import com.clearcapture.clearcapture.domain.model.Verdict
import com.clearcapture.clearcapture.domain.model.worst

/** Combines check results into one verdict and score (LLD section 6.1). Pure. */
object QualityScoreCalculator {

    /**
     * 1. A FAIL from a warning-only check becomes a WARNING, except ANALYSIS_UNAVAILABLE (the photo was not checked).
     * 2. The verdict is the worst result.
     * 3. Score = 100 − 40 per FAIL − 15 per WARNING, floored at 0. Reporting only; the verdict gates acceptance.
     */
    fun combine(stage: Stage, results: List<CheckResult>, profile: CaptureProfile, analysisMillis: Long): QualityReport {
        val adjusted = results.map { result ->
            val downgrade = result.verdict == Verdict.FAIL &&
                result.check in profile.warningOnlyChecks &&
                result.reason != ReasonCode.ANALYSIS_UNAVAILABLE
            if (downgrade) result.copy(verdict = Verdict.WARNING) else result
        }
        val verdict = adjusted.fold(Verdict.PASS) { acc, r -> acc.worst(r.verdict) }
        val fails = adjusted.count { it.verdict == Verdict.FAIL }
        val warnings = adjusted.count { it.verdict == Verdict.WARNING }
        val score = (MAX_SCORE - FAIL_PENALTY * fails - WARNING_PENALTY * warnings).coerceAtLeast(0)
        return QualityReport(stage, adjusted, verdict, score, analysisMillis)
    }

    private const val MAX_SCORE = 100
    private const val FAIL_PENALTY = 40
    private const val WARNING_PENALTY = 15
}
