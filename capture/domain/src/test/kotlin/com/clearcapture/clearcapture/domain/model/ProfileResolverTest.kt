package com.clearcapture.clearcapture.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProfileResolverTest {

    private val base = TestProfiles.container()

    @Test
    fun `override matching fixed focus lowers final blur limits`() {
        val profile = base.copy(
            deviceOverrides = listOf(
                DeviceOverride(CameraTraitsMatch(fixedFocus = true), ThresholdsPatch(finalBlurFail = 90.0, finalBlurWarn = 140.0)),
            ),
        )

        val resolved = ProfileResolver.resolve(profile, CameraTraits(fixedFocus = true, hasFlash = true, hardwareLevel = "FULL"))

        assertThat(resolved.thresholds.finalBlur).isEqualTo(BlurLimits(fail = 90.0, warn = 140.0))
        assertThat(resolved.thresholds.liveBlur).isEqualTo(base.thresholds.liveBlur)
    }

    @Test
    fun `override that does not match leaves thresholds unchanged`() {
        val profile = base.copy(
            deviceOverrides = listOf(DeviceOverride(CameraTraitsMatch(fixedFocus = true), ThresholdsPatch(lumaMin = 1.0))),
        )

        val resolved = ProfileResolver.resolve(profile, CameraTraits(fixedFocus = false, hasFlash = true, hardwareLevel = "FULL"))

        assertThat(resolved.thresholds).isEqualTo(base.thresholds)
    }

    @Test
    fun `later overrides win`() {
        val profile = base.copy(
            deviceOverrides = listOf(
                DeviceOverride(CameraTraitsMatch(), ThresholdsPatch(lumaMin = 10.0)),
                DeviceOverride(CameraTraitsMatch(hasFlash = false), ThresholdsPatch(lumaMin = 20.0)),
            ),
        )

        val resolved = ProfileResolver.resolve(profile, CameraTraits(fixedFocus = false, hasFlash = false, hardwareLevel = "LIMITED"))

        assertThat(resolved.thresholds.lumaMin).isEqualTo(20.0)
    }
}
