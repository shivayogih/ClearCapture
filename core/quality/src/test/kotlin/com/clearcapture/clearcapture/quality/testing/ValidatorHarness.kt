package com.clearcapture.clearcapture.quality.testing

import com.clearcapture.clearcapture.domain.model.CaptureProfile
import com.clearcapture.clearcapture.domain.model.CheckResult
import com.clearcapture.clearcapture.domain.model.Stage
import com.clearcapture.clearcapture.opencv.DecodedImage
import com.clearcapture.clearcapture.opencv.FrameMats
import com.clearcapture.clearcapture.opencv.JpegDecoder
import com.clearcapture.clearcapture.opencv.JpegHeaderReader
import com.clearcapture.clearcapture.opencv.MatPool
import com.clearcapture.clearcapture.quality.FrameMatsAdapter
import com.clearcapture.clearcapture.quality.FrameMatsAdapter.toNormalized
import com.clearcapture.clearcapture.quality.QualityValidator
import com.clearcapture.clearcapture.quality.SavedImageFacts
import com.clearcapture.clearcapture.quality.ValidationInput
import com.google.common.truth.Truth.assertWithMessage
import org.opencv.core.Mat

/**
 * Runs one validator on a fixture the way the analyzer does, with separate pools for the frame's own mats and
 * the validator's working mats, and checks that both pools are balanced afterwards.
 */
class ValidatorHarness {
    /** Pool for the validators' working mats. */
    val validatorPool = MatPool()

    /** Pool for the frame's mats. */
    val framePool = MatPool()

    private val decoder = JpegDecoder(ImgcodecsImageCodec())

    init {
        DesktopOpenCv.load()
    }

    /** Runs [validator] built on [validatorPool] on fixture [name] at [stage]. */
    internal fun run(
        validator: QualityValidator,
        name: String,
        stage: Stage,
        profile: CaptureProfile,
        expectedLongSide: Int? = null,
        interleaved: Boolean = false,
        rotationDegrees: Int = 0,
    ): CheckResult {
        val (mats, facts) = when (stage) {
            Stage.LIVE -> {
                val bgr = Fixtures.bgr(name)
                val frame = LiveFrames.fromBgr(bgr, interleaved = interleaved, rotationDegrees = rotationDegrees)
                bgr.release()
                FrameMatsAdapter.live(frame, profile.guide, framePool) to null
            }
            Stage.POST_CAPTURE -> {
                val file = Fixtures.file(name)
                val header = checkNotNull(JpegHeaderReader.read(file)) { "Unreadable fixture $name" }
                val decoded = decoder.decode(file, header, profile.guide?.toNormalized())
                val facts = SavedImageFacts.of(header, decoded != null, expectedLongSide)
                decoded?.let { FrameMats.fromDecoded(it, profile.guide?.toNormalized(), framePool) } to facts
            }
        }
        return runOn(validator, mats, facts, stage, profile, label = name)
    }

    /** Runs [validator] on a synthetic upright BGR image as a post-capture analysis image. */
    internal fun runOnImage(validator: QualityValidator, bgr: Mat, profile: CaptureProfile, stage: Stage): CheckResult {
        val mats = FrameMats.fromDecoded(DecodedImage(bgr, null), profile.guide?.toNormalized(), framePool)
        return runOn(validator, mats, null, stage, profile, label = "synthetic")
    }

    private fun runOn(
        validator: QualityValidator,
        mats: FrameMats?,
        facts: SavedImageFacts?,
        stage: Stage,
        profile: CaptureProfile,
        label: String,
    ): CheckResult {
        val result = try {
            val before = validatorPool.outstanding
            validator.validate(ValidationInput(stage, mats, facts), profile).also {
                assertWithMessage("validator pool balance after ${validator.check} on $label")
                    .that(validatorPool.outstanding).isEqualTo(before)
            }
        } finally {
            mats?.close()
        }
        assertWithMessage("frame pool balance after closing $label").that(framePool.outstanding).isEqualTo(0)
        println("${validator.check} $stage $label -> ${result.verdict} ${result.reason} ${result.metrics}")
        return result
    }
}
