package com.clearcapture.clearcapture.quality

import com.clearcapture.clearcapture.domain.model.CaptureProfile
import com.clearcapture.clearcapture.domain.model.CheckResult
import com.clearcapture.clearcapture.domain.model.QualityReport
import com.clearcapture.clearcapture.domain.model.ReasonCode
import com.clearcapture.clearcapture.domain.model.Stage
import com.clearcapture.clearcapture.domain.model.Verdict
import com.clearcapture.clearcapture.domain.model.worst

/**
 * LLD 6.1. A FAIL of a check in the profile's `warningOnlyChecks` is downgraded to WARNING, except for
 * `ANALYSIS_UNAVAILABLE` (design review finding 33). The worst result is the verdict. The score is
 * 100 − 40 per FAIL − 15 per WARNING, floored at 0; it is for reporting only.
 */
internal object QualityScoreCalculator {
    private const val FULL_SCORE = 100
    private const val FAIL_PENALTY = 40
    private const val WARNING_PENALTY = 15

    /** [results] with the warning-only downgrade applied. */
    fun downgrade(results: List<CheckResult>, profile: CaptureProfile): List<CheckResult> = results.map { result ->
        val downgradable = result.check in profile.warningOnlyChecks && result.reason != ReasonCode.ANALYSIS_UNAVAILABLE
        if (result.verdict == Verdict.FAIL && downgradable) result.copy(verdict = Verdict.WARNING) else result
    }

    /** The worst verdict; PASS when there are no results. */
    fun verdict(results: List<CheckResult>): Verdict = results.fold(Verdict.PASS) { acc, result -> acc.worst(result.verdict) }

    /** The score of already downgraded [results]. */
    fun score(results: List<CheckResult>): Int {
        val fails = results.count { it.verdict == Verdict.FAIL }
        val warnings = results.count { it.verdict == Verdict.WARNING }
        return (FULL_SCORE - FAIL_PENALTY * fails - WARNING_PENALTY * warnings).coerceAtLeast(0)
    }

    /** Downgrades [results] and combines them into a report. */
    fun report(stage: Stage, results: List<CheckResult>, profile: CaptureProfile, analysisMillis: Long): QualityReport {
        val final = downgrade(results, profile)
        return QualityReport(stage, final, verdict(final), score(final), analysisMillis)
    }
}
