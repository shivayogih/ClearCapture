package com.clearcapture.clearcapture.domain.hint

import com.clearcapture.clearcapture.domain.location.LocationFixPolicy
import com.clearcapture.clearcapture.domain.model.CaptureProfile
import com.clearcapture.clearcapture.domain.model.LocationStatus
import com.clearcapture.clearcapture.domain.model.QualityReport
import com.clearcapture.clearcapture.domain.model.ReasonCode
import com.clearcapture.clearcapture.domain.model.Verdict

/** Picks the single most important hint for one moment (LLD 3.4). Pure. */
object HintPrioritizer {

    /**
     * @param report the latest live report, or `null` before the first analysed frame.
     * @param analyzerAvailable false when OpenCV or live analysis is unavailable.
     */
    fun candidate(report: QualityReport?, location: LocationStatus, profile: CaptureProfile, analyzerAvailable: Boolean): Hint {
        if (!LocationFixPolicy.allowsCapture(location, profile.location)) return Hint(HintKind.LOCATION_BLOCKED, blocking = true)
        // Capture stays possible without live analysis; the saved photo is checked afterwards.
        if (!analyzerAvailable) return Hint(HintKind.ANALYZER_UNAVAILABLE, blocking = false)
        if (report == null) return Hint(HintKind.NONE, blocking = false)
        return report.results
            .filter { it.verdict != Verdict.PASS }
            .mapNotNull { result ->
                val kind = result.reason?.let(::hintFor) ?: return@mapNotNull null
                // Gating rule (d): only a live FAIL of a check that is not warning-only blocks the shutter.
                Hint(kind, blocking = result.verdict == Verdict.FAIL && result.check !in profile.warningOnlyChecks)
            }
            .minByOrNull { it.kind.ordinal }
            ?: Hint(HintKind.READY, blocking = false)
    }

    /** The guide's status for a stable hint (LLD 3.4). */
    fun guideStatus(hint: Hint, location: LocationStatus): GuideStatus = when {
        hint.kind == HintKind.NONE -> GuideStatus.Neutral
        hint.kind == HintKind.LOCATION_BLOCKED && location == LocationStatus.Acquiring -> GuideStatus.Neutral
        hint.kind == HintKind.READY -> GuideStatus.Ready
        hint.blocking -> GuideStatus.Problem
        else -> GuideStatus.Warning
    }

    private fun hintFor(reason: ReasonCode): HintKind? = when (reason) {
        ReasonCode.TOO_DARK -> HintKind.TOO_DARK
        ReasonCode.TOO_BRIGHT -> HintKind.TOO_BRIGHT
        ReasonCode.BLUR_MOTION, ReasonCode.BLUR_FOCUS -> HintKind.BLUR
        ReasonCode.GLARE_ON_SUBJECT -> HintKind.GLARE
        ReasonCode.SUBJECT_CUT_OFF -> HintKind.SUBJECT_CUT_OFF
        ReasonCode.SUBJECT_TOO_SMALL -> HintKind.SUBJECT_TOO_SMALL
        ReasonCode.SUBJECT_TOO_CLOSE -> HintKind.SUBJECT_TOO_CLOSE
        ReasonCode.OBSTRUCTED -> HintKind.OBSTRUCTION
        ReasonCode.ANALYSIS_UNAVAILABLE -> HintKind.ANALYZER_UNAVAILABLE
        else -> null
    }
}
