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
import org.opencv.core.CvType
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * LLD 5.3. Bright, unsaturated pixels (V ≥ 245, S ≤ 80) in the guide region, cleaned with a 3×3 opening, then
 * grouped into connected components. An indicator, not proof: white surfaces and sky also qualify.
 * The only validator that needs colour, so the only one that makes live analysis build `bgr` and `hsv`.
 */
internal class GlareValidator(private val pool: MatPool) : QualityValidator {
    override val check: QualityCheck = QualityCheck.GLARE
    override val stages: Set<Stage> = setOf(Stage.LIVE, Stage.POST_CAPTURE)

    override fun validate(input: ValidationInput, profile: CaptureProfile): CheckResult = guarded(check, input.stage) {
        val frame = input.mats
        val t = profile.thresholds
        pool.scoped {
            val region = roi(frame.hsv, frame.guideRect)
            val mask = mat().also { Core.inRange(region, Scalar(0.0, 0.0, MIN_VALUE), Scalar(MAX_HUE, MAX_SATURATION, MAX_VALUE), it) }
            val kernel = own(Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(KERNEL, KERNEL)))
            val opened = mat().also { Imgproc.morphologyEx(mask, it, Imgproc.MORPH_OPEN, kernel) }
            val stats = mat()
            val count = Imgproc.connectedComponentsWithStats(opened, mat(), stats, mat(), CONNECTIVITY, CvType.CV_32S)
            val columns = stats.cols()
            val table = IntArray(count * columns).also { if (count > 0) stats.get(0, 0, it) }
            val largest = (1 until count).maxOfOrNull { label -> table[label * columns + Imgproc.CC_STAT_AREA] } ?: 0
            val area = frame.guideRect.area().coerceAtLeast(1.0)
            val glarePct = Core.countNonZero(opened) / area
            val blobPct = largest / area
            val verdict = when {
                glarePct > t.glareFailPct || blobPct > t.glareBlobFailPct -> Verdict.FAIL
                glarePct > t.glareWarnPct || blobPct > t.glareBlobWarnPct -> Verdict.WARNING
                else -> Verdict.PASS
            }
            checkResult(
                check = check,
                verdict = verdict,
                reason = ReasonCode.GLARE_ON_SUBJECT,
                metrics = mapOf(
                    "glarePct" to glarePct,
                    "largestBlobPct" to blobPct,
                    "blobCount" to (count - 1).coerceAtLeast(0).toDouble(),
                ),
                thresholds = mapOf(
                    "glareWarnPct" to t.glareWarnPct,
                    "glareFailPct" to t.glareFailPct,
                    "glareBlobWarnPct" to t.glareBlobWarnPct,
                    "glareBlobFailPct" to t.glareBlobFailPct,
                ),
            )
        }
    }

    private companion object {
        const val MIN_VALUE = 245.0
        const val MAX_VALUE = 255.0
        const val MAX_SATURATION = 80.0
        const val MAX_HUE = 255.0
        const val KERNEL = 3.0
        const val CONNECTIVITY = 8
    }
}
