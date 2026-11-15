package com.clearcapture.clearcapture.quality.validator

import com.clearcapture.clearcapture.domain.model.CaptureProfile
import com.clearcapture.clearcapture.domain.model.CheckResult
import com.clearcapture.clearcapture.domain.model.QualityCheck
import com.clearcapture.clearcapture.domain.model.ReasonCode
import com.clearcapture.clearcapture.domain.model.Stage
import com.clearcapture.clearcapture.domain.model.Verdict
import com.clearcapture.clearcapture.quality.QualityValidator
import com.clearcapture.clearcapture.quality.ValidationInput
import com.clearcapture.clearcapture.quality.checkResult

/**
 * LLD 5.5, post-capture only, without OpenCV, so it still runs when OpenCV could not be loaded (D15).
 * Uses the real file dimensions, not the normalised analysis image. Every problem is a FAIL:
 * FILE_CORRUPT (no end-of-image marker, or the pixels did not decode), ORIENTATION_INVALID (EXIF value outside
 * 1..8), LOW_RESOLUTION (sides below the minimum, file smaller than `minFileBytes`, or a long side more than
 * 2 % below what the camera reported at bind time).
 */
internal class ResolutionValidator : QualityValidator {
    override val check: QualityCheck = QualityCheck.RESOLUTION
    override val stages: Set<Stage> = setOf(Stage.POST_CAPTURE)
    override val needsOpenCv: Boolean = false

    override fun validate(input: ValidationInput, profile: CaptureProfile): CheckResult {
        val t = profile.thresholds
        val thresholds = mapOf(
            "minLongSide" to t.minLongSide.toDouble(),
            "minShortSide" to t.minShortSide.toDouble(),
            "minFileBytes" to t.minFileBytes.toDouble(),
        )
        val facts = input.savedImage
            ?: return checkResult(check, Verdict.FAIL, ReasonCode.FILE_CORRUPT, emptyMap(), thresholds)
        val longSide = maxOf(facts.width, facts.height)
        val shortSide = minOf(facts.width, facts.height)
        val expected = facts.expectedLongSide
        val downscaled = expected != null && longSide < expected * (1.0 - DOWNSCALE_TOLERANCE)
        val reason = when {
            !facts.complete || facts.decoded == false -> ReasonCode.FILE_CORRUPT
            facts.orientation !in VALID_ORIENTATIONS -> ReasonCode.ORIENTATION_INVALID
            longSide < t.minLongSide || shortSide < t.minShortSide -> ReasonCode.LOW_RESOLUTION
            facts.fileBytes < t.minFileBytes || downscaled -> ReasonCode.LOW_RESOLUTION
            else -> null
        }
        val metrics = buildMap {
            put("width", facts.width.toDouble())
            put("height", facts.height.toDouble())
            put("fileBytes", facts.fileBytes.toDouble())
            put("orientation", facts.orientation.toDouble())
            if (expected != null) put("expectedLongSide", expected.toDouble())
        }
        return checkResult(
            check = check,
            verdict = if (reason == null) Verdict.PASS else Verdict.FAIL,
            reason = reason ?: ReasonCode.LOW_RESOLUTION,
            metrics = metrics,
            thresholds = if (expected != null) thresholds + ("downscaleTolerance" to DOWNSCALE_TOLERANCE) else thresholds,
        )
    }

    private companion object {
        /** 0 means the tag is absent, which is valid (treated as 1). */
        val VALID_ORIENTATIONS = 0..8
        const val DOWNSCALE_TOLERANCE = 0.02
    }
}
