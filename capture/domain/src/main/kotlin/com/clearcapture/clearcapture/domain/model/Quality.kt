package com.clearcapture.clearcapture.domain.model

/** The checks a photograph can be subjected to. */
enum class QualityCheck { BLUR, EXPOSURE, GLARE, FRAMING, RESOLUTION, OBSTRUCTION, LOCATION }

/** Outcome of a check or of a whole report. Declared in order of severity; the worst wins. */
enum class Verdict { PASS, WARNING, FAIL }

/** Where a check runs: on live preview frames or on the saved photograph. */
enum class Stage { LIVE, POST_CAPTURE }

/** Why a check did not pass. Each code has a user-facing title and advice string in `:capture:ui`. */
enum class ReasonCode {
    BLUR_MOTION, BLUR_FOCUS, TOO_DARK, TOO_BRIGHT, GLARE_ON_SUBJECT,
    SUBJECT_CUT_OFF, SUBJECT_TOO_SMALL, SUBJECT_TOO_CLOSE,
    LOW_RESOLUTION, FILE_CORRUPT, ORIENTATION_INVALID, OBSTRUCTED,
    LOCATION_PERMISSION, LOCATION_DISABLED, LOCATION_NO_FIX,
    LOCATION_STALE, LOCATION_INACCURATE, LOCATION_TAGS_MISSING, LOCATION_MOCK,

    /** OpenCV could not be loaded, so the photograph could not be checked on this device. */
    ANALYSIS_UNAVAILABLE,
}

/** Result of one check: the verdict, the reason, what was measured and what it was compared against. */
data class CheckResult(
    val check: QualityCheck,
    val verdict: Verdict,
    /** `null` exactly when [verdict] is [Verdict.PASS]. */
    val reason: ReasonCode?,
    val metrics: Map<String, Double>,
    val thresholds: Map<String, Double>,
)

/** All check results for one frame or one photograph, combined into a verdict and a score. */
data class QualityReport(
    val stage: Stage,
    val results: List<CheckResult>,
    val verdict: Verdict,
    /** 0..100, for reporting and calibration only; only [verdict] gates acceptance. */
    val score: Int,
    val analysisMillis: Long,
) {
    /** The result of [check], if it ran. */
    fun resultOf(check: QualityCheck): CheckResult? = results.firstOrNull { it.check == check }
}

/** Returns the worse of two verdicts. */
fun Verdict.worst(other: Verdict): Verdict = if (other.ordinal > ordinal) other else this
