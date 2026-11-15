package com.clearcapture.clearcapture.quality.validator

import com.clearcapture.clearcapture.domain.model.ReasonCode
import com.clearcapture.clearcapture.domain.model.Stage
import com.clearcapture.clearcapture.domain.model.TestProfiles
import com.clearcapture.clearcapture.domain.model.Verdict
import com.clearcapture.clearcapture.quality.SavedImageFacts
import com.clearcapture.clearcapture.quality.ValidationInput
import com.clearcapture.clearcapture.quality.testing.ValidatorHarness
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

/** Boundary rules of the validators on small synthetic images, where the measured values are exact. */
class ValidatorRulesTest {
    private val harness = ValidatorHarness()
    private val general = TestProfiles.general()

    private fun uniform(level: Double, width: Int = 400, height: Int = 300): Mat =
        Mat(height, width, CvType.CV_8UC3, Scalar(level, level, level))

    private fun exposureOf(level: Double) =
        harness.runOnImage(ExposureValidator(harness.validatorPool), uniform(level), general, Stage.POST_CAPTURE)

    @Test
    fun `exposure follows the 15 percent warning margin of D20 for the mean`() {
        // lumaMin 60 → warn below 69; lumaMax 200 → warn above 170.
        assertThat(exposureOf(59.0).verdict).isEqualTo(Verdict.FAIL)
        assertThat(exposureOf(65.0).let { it.verdict to it.reason }).isEqualTo(Verdict.WARNING to ReasonCode.TOO_DARK)
        assertThat(exposureOf(70.0).verdict).isEqualTo(Verdict.PASS)
        assertThat(exposureOf(169.0).verdict).isEqualTo(Verdict.PASS)
        assertThat(exposureOf(180.0).let { it.verdict to it.reason }).isEqualTo(Verdict.WARNING to ReasonCode.TOO_BRIGHT)
        assertThat(exposureOf(201.0).let { it.verdict to it.reason }).isEqualTo(Verdict.FAIL to ReasonCode.TOO_BRIGHT)
    }

    @Test
    fun `exposure follows the margin for clipped shares`() {
        // Mid-grey image with a clipped band: 32 % dark is above darkClipMax × 0.85 (29.75 %) but below 35 %.
        val image = uniform(120.0)
        Imgproc.rectangle(image, Point(0.0, 0.0), Point(399.0, 0.32 * 300 - 1), Scalar(5.0, 5.0, 5.0), -1)
        val result = harness.runOnImage(ExposureValidator(harness.validatorPool), image, general, Stage.POST_CAPTURE)
        assertThat(result.metrics.getValue("darkClipPct")).isWithin(0.005).of(0.32)
        assertThat(result.verdict to result.reason).isEqualTo(Verdict.WARNING to ReasonCode.TOO_DARK)
    }

    @Test
    fun `obstruction ignores a plain area in the middle and flags one at the border`() {
        val textured = Mat(300, 400, CvType.CV_8UC3)
        Core.randu(textured, 0.0, 255.0)
        val middle = textured.clone()
        Imgproc.rectangle(middle, Point(100.0, 75.0), Point(299.0, 224.0), Scalar(128.0, 128.0, 128.0), -1)
        val edge = textured.clone()
        Imgproc.rectangle(edge, Point(0.0, 0.0), Point(199.0, 299.0), Scalar(128.0, 128.0, 128.0), -1)
        val obstruction = ObstructionValidator(harness.validatorPool)
        val inMiddle = harness.runOnImage(obstruction, middle, general, Stage.POST_CAPTURE)
        val atEdge = harness.runOnImage(obstruction, edge, general, Stage.POST_CAPTURE)
        textured.release()
        assertThat(inMiddle.metrics.getValue("lowDetailPct")).isGreaterThan(0.0)
        assertThat(inMiddle.metrics.getValue("largestEdgeGroupPct")).isEqualTo(0.0)
        assertThat(inMiddle.verdict).isEqualTo(Verdict.PASS)
        // Half the grid is plain; the column holding the boundary may count as detailed because of its edges.
        assertThat(atEdge.metrics.getValue("largestEdgeGroupPct")).isAtLeast(0.375)
        assertThat(atEdge.verdict to atEdge.reason).isEqualTo(Verdict.FAIL to ReasonCode.OBSTRUCTED)
    }

    @Test
    fun `glare uses the blob limits as well as the total share`() {
        val image = uniform(100.0)
        // One white blob of 2 % of the image: above glareBlobWarnPct (1 %), below glareBlobFailPct (3 %).
        Imgproc.rectangle(image, Point(0.0, 0.0), Point(39.0, 59.0), Scalar(255.0, 255.0, 255.0), -1)
        val result = harness.runOnImage(GlareValidator(harness.validatorPool), image, general, Stage.POST_CAPTURE)
        assertThat(result.metrics.getValue("largestBlobPct")).isWithin(0.001).of(0.02)
        assertThat(result.verdict to result.reason).isEqualTo(Verdict.WARNING to ReasonCode.GLARE_ON_SUBJECT)
    }

    @Test
    fun `resolution reports an invalid orientation and accepts a missing one`() {
        val facts = SavedImageFacts(2000, 1500, 400_000, 9, complete = true, decoded = true, expectedLongSide = null)
        val invalid = ResolutionValidator().validate(ValidationInput(Stage.POST_CAPTURE, null, facts), general)
        val absent = ResolutionValidator().validate(ValidationInput(Stage.POST_CAPTURE, null, facts.copy(orientation = 0)), general)
        assertThat(invalid.verdict to invalid.reason).isEqualTo(Verdict.FAIL to ReasonCode.ORIENTATION_INVALID)
        assertThat(absent.verdict).isEqualTo(Verdict.PASS)
    }

    @Test
    fun `resolution works without OpenCV facts and reports an undecodable file as corrupt`() {
        val facts = SavedImageFacts(2000, 1500, 400_000, 1, complete = true, decoded = false, expectedLongSide = null)
        val result = ResolutionValidator().validate(ValidationInput(Stage.POST_CAPTURE, null, facts), general)
        val noFacts = ResolutionValidator().validate(ValidationInput(Stage.POST_CAPTURE, null, null), general)
        assertThat(result.reason).isEqualTo(ReasonCode.FILE_CORRUPT)
        assertThat(noFacts.reason).isEqualTo(ReasonCode.FILE_CORRUPT)
    }

    @Test
    fun `a validator without mats reports analysis unavailable instead of throwing`() {
        val live = BlurValidator(harness.validatorPool).validate(ValidationInput(Stage.LIVE, null, null), general)
        val final = BlurValidator(harness.validatorPool).validate(ValidationInput(Stage.POST_CAPTURE, null, null), general)
        assertThat(live.verdict to live.reason).isEqualTo(Verdict.WARNING to ReasonCode.ANALYSIS_UNAVAILABLE)
        assertThat(final.verdict to final.reason).isEqualTo(Verdict.FAIL to ReasonCode.ANALYSIS_UNAVAILABLE)
        assertThat(harness.validatorPool.outstanding).isEqualTo(0)
    }
}
