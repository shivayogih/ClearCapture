package com.clearcapture.clearcapture.quality

import com.clearcapture.clearcapture.domain.model.CaptureProfile
import com.clearcapture.clearcapture.domain.model.CheckResult
import com.clearcapture.clearcapture.domain.model.QualityCheck
import com.clearcapture.clearcapture.domain.model.ReasonCode
import com.clearcapture.clearcapture.domain.model.Stage
import com.clearcapture.clearcapture.domain.model.Verdict
import com.clearcapture.clearcapture.opencv.FrameMats
import com.clearcapture.clearcapture.opencv.JpegHeader
import org.opencv.core.CvException

/**
 * One image-quality check (LLD 5). Implementations are deterministic, measure properties and geometry only,
 * must not throw, must not keep references to the input mats, and take their working mats from the pool.
 */
internal interface QualityValidator {
    /** The check this validator answers. */
    val check: QualityCheck

    /** The stages it runs in. */
    val stages: Set<Stage>

    /** False for checks that work without OpenCV; they still run when OpenCV could not be loaded (D15). */
    val needsOpenCv: Boolean get() = true

    /** Live analysis runs this validator on every n-th frame and reuses its last result in between. */
    val liveEveryNthFrame: Int get() = 1

    /** False when [profile] gives this check nothing to measure; the check is then skipped. */
    fun appliesTo(profile: CaptureProfile): Boolean = true

    /** Measures [input] against [profile]'s thresholds. */
    fun validate(input: ValidationInput, profile: CaptureProfile): CheckResult
}

/** Facts about the saved file that the resolution and integrity check needs. No OpenCV involved. */
internal data class SavedImageFacts(
    val width: Int,
    val height: Int,
    val fileBytes: Long,
    val orientation: Int,
    /** The file ends with an end-of-image marker. */
    val complete: Boolean,
    /** Whether the pixels decoded; `null` when decoding was not attempted (OpenCV unavailable). */
    val decoded: Boolean?,
    /** Long side the camera reported at bind time, if known. */
    val expectedLongSide: Int?,
) {
    companion object {
        /** Facts from a parsed header. */
        fun of(header: JpegHeader, decoded: Boolean?, expectedLongSide: Int?): SavedImageFacts = SavedImageFacts(
            width = header.width,
            height = header.height,
            fileBytes = header.fileBytes,
            orientation = header.orientation,
            complete = header.complete,
            decoded = decoded,
            expectedLongSide = expectedLongSide,
        )
    }
}

/**
 * What a validator gets: the stage, the frame's mats (absent when they could not be built) and, post-capture,
 * the facts about the saved file.
 */
internal class ValidationInput(
    val stage: Stage,
    private val frame: FrameMats?,
    val savedImage: SavedImageFacts?,
) {
    /** The frame's mats. Only OpenCV-based validators ask, and only when the analyzer built them. */
    val mats: FrameMats get() = checkNotNull(frame) { "No mats for this input" }
}

/** A result whose reason is dropped when it passes, so `reason == null` exactly when the verdict is PASS. */
internal fun checkResult(
    check: QualityCheck,
    verdict: Verdict,
    reason: ReasonCode,
    metrics: Map<String, Double>,
    thresholds: Map<String, Double>,
): CheckResult = CheckResult(check, verdict, if (verdict == Verdict.PASS) null else reason, metrics, thresholds)

/**
 * The result of a check that could not run because OpenCV is unusable. Post-capture it is a FAIL (D15);
 * live it is a WARNING, because an unavailable analyzer must never disable the shutter (LLD 3.4).
 */
internal fun analysisUnavailable(check: QualityCheck, stage: Stage): CheckResult = CheckResult(
    check = check,
    verdict = if (stage == Stage.LIVE) Verdict.WARNING else Verdict.FAIL,
    reason = ReasonCode.ANALYSIS_UNAVAILABLE,
    metrics = emptyMap(),
    thresholds = emptyMap(),
)

/** Runs [measure]; an OpenCV or state error becomes [analysisUnavailable] instead of an exception. */
internal inline fun guarded(check: QualityCheck, stage: Stage, measure: () -> CheckResult): CheckResult = try {
    measure()
} catch (_: CvException) {
    analysisUnavailable(check, stage)
} catch (_: IllegalArgumentException) {
    analysisUnavailable(check, stage)
} catch (_: IllegalStateException) {
    analysisUnavailable(check, stage)
} catch (_: UnsatisfiedLinkError) {
    analysisUnavailable(check, stage)
}

/** 1.0 for true, 0.0 for false, for metrics maps. */
internal fun Boolean.asMetric(): Double = if (this) 1.0 else 0.0
