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
import com.clearcapture.clearcapture.quality.asMetric
import com.clearcapture.clearcapture.quality.checkResult
import com.clearcapture.clearcapture.quality.guarded
import org.opencv.core.MatOfPoint
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * LLD 5.4. Edges (Gaussian 5×5, Canny 80/160, one dilation), external contours of at least 0.5 % of the frame
 * (by bounding-box area), and the union bounding box of those that overlap the guide. Coverage is that box's
 * area over the guide's; the subject is cut off when the box comes within 2 % of an image edge. Geometry only:
 * it does not recognise what the subject is. Skipped when the profile has no guide.
 */
internal class FramingValidator(private val pool: MatPool) : QualityValidator {
    override val check: QualityCheck = QualityCheck.FRAMING
    override val stages: Set<Stage> = setOf(Stage.LIVE, Stage.POST_CAPTURE)
    override val liveEveryNthFrame: Int = 3

    override fun appliesTo(profile: CaptureProfile): Boolean = profile.guide != null

    override fun validate(input: ValidationInput, profile: CaptureProfile): CheckResult = guarded(check, input.stage) {
        val frame = input.mats
        val t = profile.thresholds
        val width = frame.width
        val height = frame.height
        val (box, kept) = pool.scoped {
            val blurred = mat().also { Imgproc.GaussianBlur(frame.gray, it, Size(BLUR_KERNEL, BLUR_KERNEL), 0.0) }
            val edges = mat().also { Imgproc.Canny(blurred, it, CANNY_LOW, CANNY_HIGH) }
            val kernel = own(Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(DILATE_KERNEL, DILATE_KERNEL)))
            val dilated = mat().also { Imgproc.dilate(edges, it, kernel) }
            val contours = ArrayList<MatOfPoint>()
            try {
                Imgproc.findContours(dilated, contours, mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            } finally {
                contours.forEach { own(it) }
            }
            val minArea = MIN_CONTOUR_SHARE * width * height
            val large = contours.map(Imgproc::boundingRect).filter { it.area() >= minArea }
            large.filter { it.overlaps(frame.guideRect) }.reduceOrNull(::union) to large.size
        }
        val coverage = box?.let { it.area() / frame.guideRect.area().coerceAtLeast(1.0) } ?: 0.0
        val marginX = BORDER_SHARE * width
        val marginY = BORDER_SHARE * height
        val borderTouch = box != null && (
            box.x <= marginX || box.y <= marginY || box.x + box.width >= width - marginX || box.y + box.height >= height - marginY
            )
        val (verdict, reason) = when {
            borderTouch -> Verdict.FAIL to ReasonCode.SUBJECT_CUT_OFF
            coverage < t.coverageMin -> Verdict.FAIL to ReasonCode.SUBJECT_TOO_SMALL
            coverage > t.coverageMax -> Verdict.FAIL to ReasonCode.SUBJECT_TOO_CLOSE
            else -> Verdict.PASS to ReasonCode.SUBJECT_TOO_SMALL
        }
        checkResult(
            check = check,
            verdict = verdict,
            reason = reason,
            metrics = mapOf("coverage" to coverage, "borderTouch" to borderTouch.asMetric(), "contourCount" to kept.toDouble()),
            thresholds = mapOf("coverageMin" to t.coverageMin, "coverageMax" to t.coverageMax, "borderMargin" to BORDER_SHARE),
        )
    }

    private fun Rect.overlaps(other: Rect): Boolean =
        x < other.x + other.width && other.x < x + width && y < other.y + other.height && other.y < y + height

    private fun union(a: Rect, b: Rect): Rect {
        val left = minOf(a.x, b.x)
        val top = minOf(a.y, b.y)
        return Rect(left, top, maxOf(a.x + a.width, b.x + b.width) - left, maxOf(a.y + a.height, b.y + b.height) - top)
    }

    private companion object {
        const val BLUR_KERNEL = 5.0
        const val DILATE_KERNEL = 3.0
        const val CANNY_LOW = 80.0
        const val CANNY_HIGH = 160.0
        const val MIN_CONTOUR_SHARE = 0.005
        const val BORDER_SHARE = 0.02
    }
}
