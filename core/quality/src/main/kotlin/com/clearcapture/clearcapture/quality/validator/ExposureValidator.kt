package com.clearcapture.clearcapture.quality.validator

import com.clearcapture.clearcapture.domain.model.CaptureProfile
import com.clearcapture.clearcapture.domain.model.CheckResult
import com.clearcapture.clearcapture.domain.model.QualityCheck
import com.clearcapture.clearcapture.domain.model.ReasonCode
import com.clearcapture.clearcapture.domain.model.Stage
import com.clearcapture.clearcapture.domain.model.Verdict
import com.clearcapture.clearcapture.opencv.MatPool
import com.clearcapture.clearcapture.opencv.scoped
import com.clearcapture.clearcapture.quality.QualityValidator
import com.clearcapture.clearcapture.quality.ValidationInput
import com.clearcapture.clearcapture.quality.checkResult
import com.clearcapture.clearcapture.quality.guarded
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfFloat
import org.opencv.core.MatOfInt
import org.opencv.imgproc.Imgproc

/**
 * LLD 5.2. Mean luminance and the shares of clipped dark (≤ 16) and bright (≥ 240) pixels of the guide region.
 * WARNING inside the 15 % margin of decision D20: mean < lumaMin × 1.15 or dark share > darkClipMax × 0.85
 * (TOO_DARK); mean > lumaMax × 0.85 or bright share > brightClipMax × 0.85 (TOO_BRIGHT). Dark is reported first.
 */
internal class ExposureValidator(private val pool: MatPool) : QualityValidator {
    override val check: QualityCheck = QualityCheck.EXPOSURE
    override val stages: Set<Stage> = setOf(Stage.LIVE, Stage.POST_CAPTURE)

    override fun validate(input: ValidationInput, profile: CaptureProfile): CheckResult = guarded(check, input.stage) {
        val frame = input.mats
        val t = profile.thresholds
        val (meanLuma, histogram) = pool.scoped {
            val region = roi(frame.gray, frame.guideRect)
            val hist = mat()
            Imgproc.calcHist(
                listOf(region),
                own(MatOfInt(0)),
                own(Mat()),
                hist,
                own(MatOfInt(BINS)),
                own(MatOfFloat(0f, BINS.toFloat())),
            )
            Core.mean(region).`val`[0] to FloatArray(BINS).also { hist.get(0, 0, it) }
        }
        val total = histogram.sum().toDouble().coerceAtLeast(1.0)
        val dark = (0..DARK_LEVEL).sumOf { histogram[it].toDouble() } / total
        val bright = (BRIGHT_LEVEL until BINS).sumOf { histogram[it].toDouble() } / total
        val (verdict, reason) = when {
            meanLuma < t.lumaMin || dark > t.darkClipMax -> Verdict.FAIL to ReasonCode.TOO_DARK
            meanLuma > t.lumaMax || bright > t.brightClipMax -> Verdict.FAIL to ReasonCode.TOO_BRIGHT
            meanLuma < t.lumaMin * LOW_MARGIN || dark > t.darkClipMax * HIGH_MARGIN -> Verdict.WARNING to ReasonCode.TOO_DARK
            meanLuma > t.lumaMax * HIGH_MARGIN || bright > t.brightClipMax * HIGH_MARGIN -> Verdict.WARNING to ReasonCode.TOO_BRIGHT
            else -> Verdict.PASS to ReasonCode.TOO_DARK
        }
        checkResult(
            check = check,
            verdict = verdict,
            reason = reason,
            metrics = mapOf("meanLuma" to meanLuma, "darkClipPct" to dark, "brightClipPct" to bright),
            thresholds = mapOf(
                "lumaMin" to t.lumaMin,
                "lumaMax" to t.lumaMax,
                "darkClipMax" to t.darkClipMax,
                "brightClipMax" to t.brightClipMax,
                "warnMargin" to WARN_MARGIN,
            ),
        )
    }

    private companion object {
        const val BINS = 256
        const val DARK_LEVEL = 16
        const val BRIGHT_LEVEL = 240
        const val WARN_MARGIN = 0.15
        const val LOW_MARGIN = 1.0 + WARN_MARGIN
        const val HIGH_MARGIN = 1.0 - WARN_MARGIN
    }
}
