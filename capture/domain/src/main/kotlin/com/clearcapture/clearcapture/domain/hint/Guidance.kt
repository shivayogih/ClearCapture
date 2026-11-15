package com.clearcapture.clearcapture.domain.hint

import com.clearcapture.clearcapture.domain.model.LocationStatus
import com.clearcapture.clearcapture.domain.model.QualityReport

/** The live hints, highest priority first (LLD section 3.4 is the single definition of this order). */
enum class HintKind {
    LOCATION_BLOCKED,
    ANALYZER_UNAVAILABLE,
    TOO_DARK,
    TOO_BRIGHT,
    BLUR,
    GLARE,
    SUBJECT_CUT_OFF,
    SUBJECT_TOO_SMALL,
    SUBJECT_TOO_CLOSE,
    OBSTRUCTION,
    READY,

    /** Aiming, no stable result yet. */
    NONE,
}

/** The colour state of the framing guide. Never shown by colour alone. */
enum class GuideStatus { Neutral, Ready, Warning, Problem }

/** One stable hint. [blocking] is true when it disables the shutter (gating rule, LLD 3.4). */
data class Hint(val kind: HintKind, val blocking: Boolean)

/** Everything the camera screen shows from live analysis and location. */
data class LiveGuidance(
    val hint: Hint,
    val guideStatus: GuideStatus,
    /** Covers gating rules (c) location and (d) live FAIL; the screen adds camera-ready and capturing. */
    val captureEnabled: Boolean,
    val report: QualityReport?,
    val location: LocationStatus,
) {
    /** Factory values. */
    companion object {
        /** Before any frame has been analysed, with location not yet known. */
        val Initial: LiveGuidance = LiveGuidance(
            hint = Hint(HintKind.NONE, blocking = false),
            guideStatus = GuideStatus.Neutral,
            captureEnabled = true,
            report = null,
            location = LocationStatus.Acquiring,
        )
    }
}
