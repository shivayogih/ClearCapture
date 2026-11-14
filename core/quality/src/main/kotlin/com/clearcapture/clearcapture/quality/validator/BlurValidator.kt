package com.clearcapture.clearcapture.quality.validator

import com.clearcapture.clearcapture.domain.model.CaptureProfile
import com.clearcapture.clearcapture.domain.model.CheckResult
import com.clearcapture.clearcapture.domain.model.QualityCheck
import com.clearcapture.clearcapture.domain.model.ReasonCode
import com.clearcapture.clearcapture.domain.model.Stage
import com.clearcapture.clearcapture.domain.model.Verdict
import com.clearcapture.clearcapture.opencv.MatPool
import com.clearcapture.clearcapture.opencv.MatScope
import com.clearcapture.clearcapture.opencv.scoped
import com.clearcapture.clearcapture.quality.QualityValidator
import com.clearcapture.clearcapture.quality.ValidationInput
import com.clearcapture.clearcapture.quality.checkResult
import com.clearcapture.clearcapture.quality.guarded
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.imgproc.Imgproc

/**
 * LLD 5.1. Laplacian variance of the guide region (post-capture: of the full-resolution `sharpCrop`) against
 * the stage's limits. The ratio of horizontal to vertical gradient energy separates motion blur from defocus;
 * it is a heuristic, and diagonal motion reads as defocus.
 */
internal class BlurValidator(private val pool: MatPool) : QualityValidator {
    override val check: QualityCheck = QualityCheck.BLUR
    override val stages: Set<Stage> = setOf(Stage.LIVE, Stage.POST_CAPTURE)

    override fun validate(input: ValidationInput, profile: CaptureProfile): CheckResult = guarded(check, input.stage) {
        val frame = input.mats
        val limits = profile.thresholds.blur(input.stage)
        pool.scoped {
            val crop = if (input.stage == Stage.POST_CAPTURE) frame.sharpCrop else null
            val region = crop ?: roi(frame.gray, frame.guideRect)
            val lapVarRegion = laplacianVariance(region)
            val lapVarFull = laplacianVariance(frame.gray)
            val gx = mat().also { Imgproc.Sobel(region, it, CvType.CV_32F, 1, 0) }
            val gy = mat().also { Imgproc.Sobel(region, it, CvType.CV_32F, 0, 1) }
            val magnitude = mat().also { Core.magnitude(gx, gy, it) }
            val tenengrad = Core.mean(magnitude).`val`[0]
            val dirRatio = directionalRatio(Core.norm(gx, Core.NORM_L2SQR), Core.norm(gy, Core.NORM_L2SQR))
            val verdict = when {
                lapVarRegion < limits.fail -> Verdict.FAIL
                lapVarRegion < limits.warn -> Verdict.WARNING
                else -> Verdict.PASS
            }
            val motion = dirRatio < MOTION_RATIO_MIN || dirRatio > MOTION_RATIO_MAX
            checkResult(
                check = check,
                verdict = verdict,
                reason = if (motion) ReasonCode.BLUR_MOTION else ReasonCode.BLUR_FOCUS,
                metrics = mapOf(
                    "lapVarRegion" to lapVarRegion,
                    "lapVarFull" to lapVarFull,
                    "tenengrad" to tenengrad,
                    "dirRatio" to dirRatio,
                ),
                thresholds = mapOf(
                    "blurFail" to limits.fail,
                    "blurWarn" to limits.warn,
                    "dirRatioMin" to MOTION_RATIO_MIN,
                    "dirRatioMax" to MOTION_RATIO_MAX,
                ),
            )
        }
    }

    /** Variance of the Laplacian; computed on the signed 16-bit response so no value is clipped. */
    private fun MatScope.laplacianVariance(source: Mat): Double {
        val laplacian = mat().also { Imgproc.Laplacian(source, it, CvType.CV_16S) }
        val mean = own(MatOfDouble())
        val stdDev = own(MatOfDouble())
        Core.meanStdDev(laplacian, mean, stdDev)
        val sigma = stdDev.toArray().firstOrNull() ?: 0.0
        return sigma * sigma
    }

    private fun directionalRatio(horizontalEnergy: Double, verticalEnergy: Double): Double = when {
        verticalEnergy > 0.0 -> (horizontalEnergy / verticalEnergy).coerceAtMost(RATIO_CAP)
        horizontalEnergy > 0.0 -> RATIO_CAP
        else -> 1.0
    }

    private companion object {
        const val MOTION_RATIO_MIN = 0.5
        const val MOTION_RATIO_MAX = 2.0
        const val RATIO_CAP = 1000.0
    }
}
