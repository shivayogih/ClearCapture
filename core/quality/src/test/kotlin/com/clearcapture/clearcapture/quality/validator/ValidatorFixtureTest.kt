package com.clearcapture.clearcapture.quality.validator

import com.clearcapture.clearcapture.domain.model.CaptureProfile
import com.clearcapture.clearcapture.domain.model.CheckResult
import com.clearcapture.clearcapture.domain.model.ReasonCode
import com.clearcapture.clearcapture.domain.model.Stage
import com.clearcapture.clearcapture.domain.model.TestProfiles
import com.clearcapture.clearcapture.domain.model.Verdict
import com.clearcapture.clearcapture.quality.QualityValidator
import com.clearcapture.clearcapture.quality.testing.Fixtures
import com.clearcapture.clearcapture.quality.testing.ValidatorHarness
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * Each validator on the committed fixtures, live (640×480 YUV frames made from the fixture) and post-capture
 * (the JPEG decoded the production way): it passes on the sharp scene, gives the right reason on each bad one,
 * and leaves both mat pools balanced (checked inside [ValidatorHarness]).
 */
class ValidatorFixtureTest {
    private val harness = ValidatorHarness()
    private val container = TestProfiles.container()
    private val general = TestProfiles.general()

    private fun both(validator: QualityValidator, name: String, profile: CaptureProfile = container): List<CheckResult> =
        listOf(Stage.LIVE, Stage.POST_CAPTURE).map { harness.run(validator, name, it, profile) }

    private fun assertAll(results: List<CheckResult>, verdict: Verdict, reason: ReasonCode?) {
        results.forEach { result ->
            assertWithMessage("${result.check} ${result.metrics}").that(result.verdict).isEqualTo(verdict)
            assertWithMessage("${result.check} ${result.metrics}").that(result.reason).isEqualTo(reason)
        }
    }

    @Test
    fun `blur passes on the sharp scene`() {
        val blur = BlurValidator(harness.validatorPool)
        assertAll(both(blur, Fixtures.SHARP), Verdict.PASS, null)
        assertAll(both(blur, Fixtures.SHARP, general), Verdict.PASS, null)
    }

    @Test
    fun `blur reports motion for directional blur`() {
        assertAll(both(BlurValidator(harness.validatorPool), Fixtures.MOTION_BLUR), Verdict.FAIL, ReasonCode.BLUR_MOTION)
    }

    @Test
    fun `blur reports focus for defocus`() {
        assertAll(both(BlurValidator(harness.validatorPool), Fixtures.DEFOCUS), Verdict.FAIL, ReasonCode.BLUR_FOCUS)
    }

    @Test
    fun `blur reports every metric and the stage limits`() {
        val blur = BlurValidator(harness.validatorPool)
        val live = harness.run(blur, Fixtures.SHARP, Stage.LIVE, container)
        val final = harness.run(blur, Fixtures.SHARP, Stage.POST_CAPTURE, container)
        assertThat(live.metrics.keys).containsExactly("lapVarRegion", "lapVarFull", "tenengrad", "dirRatio")
        assertThat(live.thresholds["blurFail"]).isEqualTo(60.0)
        assertThat(final.thresholds["blurFail"]).isEqualTo(120.0)
    }

    @Test
    fun `exposure passes on the sharp scene`() {
        val exposure = ExposureValidator(harness.validatorPool)
        assertAll(both(exposure, Fixtures.SHARP), Verdict.PASS, null)
        assertAll(both(exposure, Fixtures.SHARP, general), Verdict.PASS, null)
    }

    @Test
    fun `exposure reports dark and bright scenes`() {
        val exposure = ExposureValidator(harness.validatorPool)
        assertAll(both(exposure, Fixtures.DARK), Verdict.FAIL, ReasonCode.TOO_DARK)
        assertAll(both(exposure, Fixtures.OVEREXPOSED), Verdict.FAIL, ReasonCode.TOO_BRIGHT)
    }

    @Test
    fun `glare passes on the sharp scene and fails on a specular highlight`() {
        val glare = GlareValidator(harness.validatorPool)
        assertAll(both(glare, Fixtures.SHARP), Verdict.PASS, null)
        assertAll(both(glare, Fixtures.GLARE), Verdict.FAIL, ReasonCode.GLARE_ON_SUBJECT)
    }

    @Test
    fun `glare works on interleaved chroma and rotated frames`() {
        val glare = GlareValidator(harness.validatorPool)
        val interleaved = harness.run(glare, Fixtures.GLARE, Stage.LIVE, container, interleaved = true)
        val rotated = harness.run(glare, Fixtures.GLARE, Stage.LIVE, container, interleaved = true, rotationDegrees = 90)
        val sharp = harness.run(glare, Fixtures.SHARP, Stage.LIVE, container, rotationDegrees = 270)
        assertAll(listOf(interleaved, rotated), Verdict.FAIL, ReasonCode.GLARE_ON_SUBJECT)
        assertAll(listOf(sharp), Verdict.PASS, null)
    }

    @Test
    fun `framing passes on the sharp scene`() {
        assertAll(both(FramingValidator(harness.validatorPool), Fixtures.SHARP), Verdict.PASS, null)
    }

    @Test
    fun `framing reports a cut-off and a too-small subject`() {
        val framing = FramingValidator(harness.validatorPool)
        assertAll(both(framing, Fixtures.CUT_OFF), Verdict.FAIL, ReasonCode.SUBJECT_CUT_OFF)
        assertAll(both(framing, Fixtures.TOO_SMALL), Verdict.FAIL, ReasonCode.SUBJECT_TOO_SMALL)
    }

    @Test
    fun `framing reports a subject larger than the guide as too close`() {
        val tight = container.copy(thresholds = container.thresholds.copy(coverageMax = 0.5))
        val results = listOf(Stage.LIVE, Stage.POST_CAPTURE)
            .map { harness.run(FramingValidator(harness.validatorPool), Fixtures.SHARP, it, tight) }
        assertAll(results, Verdict.FAIL, ReasonCode.SUBJECT_TOO_CLOSE)
    }

    @Test
    fun `framing is skipped without a guide`() {
        assertThat(FramingValidator(harness.validatorPool).appliesTo(general)).isFalse()
        assertThat(FramingValidator(harness.validatorPool).appliesTo(container)).isTrue()
    }

    @Test
    fun `obstruction passes on the sharp scene with and without a guide`() {
        val obstruction = ObstructionValidator(harness.validatorPool)
        assertAll(both(obstruction, Fixtures.SHARP), Verdict.PASS, null)
        assertAll(both(obstruction, Fixtures.SHARP, general), Verdict.PASS, null)
    }

    @Test
    fun `obstruction reports a finger over the lens`() {
        val obstruction = ObstructionValidator(harness.validatorPool)
        assertAll(both(obstruction, Fixtures.OBSTRUCTED), Verdict.FAIL, ReasonCode.OBSTRUCTED)
        assertAll(both(obstruction, Fixtures.OBSTRUCTED, general), Verdict.FAIL, ReasonCode.OBSTRUCTED)
    }

    @Test
    fun `resolution passes on the sharp file`() {
        val result = harness.run(ResolutionValidator(), Fixtures.SHARP, Stage.POST_CAPTURE, container)
        assertThat(result.verdict).isEqualTo(Verdict.PASS)
        assertThat(result.metrics["width"]).isEqualTo(2000.0)
        assertThat(result.metrics["height"]).isEqualTo(1500.0)
    }

    @Test
    fun `resolution reports low resolution, truncation and unexpected downscaling`() {
        val resolution = ResolutionValidator()
        val low = harness.run(resolution, Fixtures.LOW_RESOLUTION, Stage.POST_CAPTURE, container)
        val truncated = harness.run(resolution, Fixtures.TRUNCATED, Stage.POST_CAPTURE, container)
        val downscaled = harness.run(resolution, Fixtures.SHARP, Stage.POST_CAPTURE, container, expectedLongSide = 4000)
        val expected = harness.run(resolution, Fixtures.SHARP, Stage.POST_CAPTURE, container, expectedLongSide = 2000)
        assertThat(low.reason).isEqualTo(ReasonCode.LOW_RESOLUTION)
        assertThat(truncated.reason).isEqualTo(ReasonCode.FILE_CORRUPT)
        assertThat(downscaled.reason).isEqualTo(ReasonCode.LOW_RESOLUTION)
        assertThat(downscaled.metrics["expectedLongSide"]).isEqualTo(4000.0)
        assertThat(expected.verdict).isEqualTo(Verdict.PASS)
        listOf(low, truncated, downscaled).forEach { assertThat(it.verdict).isEqualTo(Verdict.FAIL) }
    }

    @Test
    fun `resolution reports a file smaller than the minimum size`() {
        val strict = container.copy(thresholds = container.thresholds.copy(minFileBytes = 10_000_000))
        val result = harness.run(ResolutionValidator(), Fixtures.SHARP, Stage.POST_CAPTURE, strict)
        assertThat(result.verdict).isEqualTo(Verdict.FAIL)
        assertThat(result.reason).isEqualTo(ReasonCode.LOW_RESOLUTION)
    }
}
