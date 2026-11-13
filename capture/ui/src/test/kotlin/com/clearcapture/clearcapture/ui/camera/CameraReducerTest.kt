package com.clearcapture.clearcapture.ui.camera

import com.clearcapture.clearcapture.domain.hint.GuideStatus
import com.clearcapture.clearcapture.domain.hint.Hint
import com.clearcapture.clearcapture.domain.hint.HintKind
import com.clearcapture.clearcapture.domain.hint.LiveGuidance
import com.clearcapture.clearcapture.domain.model.BlurLimits
import com.clearcapture.clearcapture.domain.model.CameraCapabilities
import com.clearcapture.clearcapture.domain.model.CaptureError
import com.clearcapture.clearcapture.domain.model.CameraTraitsMatch
import com.clearcapture.clearcapture.domain.model.DeviceOverride
import com.clearcapture.clearcapture.domain.model.LocationFix
import com.clearcapture.clearcapture.domain.model.LocationStatus
import com.clearcapture.clearcapture.domain.model.TestProfiles
import com.clearcapture.clearcapture.domain.model.ThresholdsPatch
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** One test per row of the LLD 4.1 transition table, plus the shutter gating rule. */
class CameraReducerTest {

    private val caps = CameraCapabilities(
        cameraId = "0", previewSize = null, analysisSize = null, captureSize = null,
        hasFlash = true, hasAutoFocus = false, hardwareLevel = "FULL",
    )
    private val fix = LocationFix(1.0, 2.0, null, 4.4f, 0, "gps", false)
    private val profile = TestProfiles.container().copy(
        deviceOverrides = listOf(DeviceOverride(CameraTraitsMatch(fixedFocus = true), ThresholdsPatch(finalBlurFail = 90.0))),
    )

    private fun reduce(vararg changes: CameraChange) = changes.fold(CameraUiState()) { s, c -> CameraReducer.reduce(s, c) }

    private fun guidance(kind: HintKind, enabled: Boolean, location: LocationStatus = LocationStatus.Ready(fix)) =
        CameraChange.Guidance(LiveGuidance(Hint(kind, !enabled), GuideStatus.Ready, enabled, null, location))

    @Test
    fun `initial state has the shutter disabled because the camera is starting`() {
        val state = CameraUiState()
        assertThat(state.captureEnabled).isFalse()
        assertThat(state.captureDisabledReason).isEqualTo(ShutterBlock.CAMERA_NOT_READY)
    }

    @Test
    fun `profile loaded`() {
        assertThat(reduce(CameraChange.ProfileLoaded(profile)).profile).isEqualTo(profile)
    }

    @Test
    fun `camera ready enables torch and resolves device overrides`() {
        val state = reduce(CameraChange.ProfileLoaded(profile), CameraChange.CameraReady(caps, analysisAvailable = true))
        assertThat(state.camera).isEqualTo(CameraStatus.Ready)
        assertThat(state.torchAvailable).isTrue()
        assertThat(state.focusAvailable).isFalse()
        assertThat(state.profile?.thresholds?.finalBlur).isEqualTo(BlurLimits(90.0, 180.0))
        assertThat(state.captureEnabled).isTrue()
    }

    @Test
    fun `camera failed disables capture`() {
        val state = reduce(CameraChange.CameraReady(caps, true), CameraChange.CameraFailed(CaptureError.CameraInUse))
        assertThat(state.camera).isEqualTo(CameraStatus.Error(CaptureError.CameraInUse))
        assertThat(state.captureEnabled).isFalse()
    }

    @Test
    fun `live warning keeps the shutter enabled`() {
        val state = reduce(CameraChange.CameraReady(caps, true), guidance(HintKind.GLARE, enabled = true))
        assertThat(state.hint).isEqualTo(HintKind.GLARE)
        assertThat(state.captureEnabled).isTrue()
    }

    @Test
    fun `blocking quality hint disables the shutter with the quality reason`() {
        val state = reduce(CameraChange.CameraReady(caps, true), guidance(HintKind.BLUR, enabled = false))
        assertThat(state.captureEnabled).isFalse()
        assertThat(state.captureDisabledReason).isEqualTo(ShutterBlock.QUALITY)
    }

    @Test
    fun `location block disables the shutter with the location reason`() {
        val state = reduce(
            CameraChange.CameraReady(caps, true),
            guidance(HintKind.LOCATION_BLOCKED, enabled = false, location = LocationStatus.Acquiring),
        )
        assertThat(state.captureDisabledReason).isEqualTo(ShutterBlock.LOCATION)
        assertThat(state.location).isEqualTo(LocationChipUi.Acquiring)
    }

    @Test
    fun `capture started and finished`() {
        val started = reduce(CameraChange.CameraReady(caps, true), CameraChange.CaptureStarted)
        assertThat(started.captureEnabled).isFalse()
        assertThat(started.captureDisabledReason).isEqualTo(ShutterBlock.CAPTURING)
        val finished = CameraReducer.reduce(started, CameraChange.CaptureFinished)
        assertThat(finished.captureEnabled).isTrue()
    }

    @Test
    fun `guidance block survives a capture`() {
        val state = reduce(
            CameraChange.CameraReady(caps, true),
            guidance(HintKind.BLUR, enabled = false),
            CameraChange.CaptureStarted,
            CameraChange.CaptureFinished,
        )
        assertThat(state.captureDisabledReason).isEqualTo(ShutterBlock.QUALITY)
    }

    @Test
    fun `camera becoming ready after a block keeps the block`() {
        val state = reduce(guidance(HintKind.BLUR, enabled = false), CameraChange.CameraReady(caps, true))
        assertThat(state.captureDisabledReason).isEqualTo(ShutterBlock.QUALITY)
    }

    @Test
    fun `torch changed`() {
        assertThat(reduce(CameraChange.TorchChanged(true)).torchOn).isTrue()
    }

    @Test
    fun `location chip shows accuracy`() {
        val state = reduce(CameraChange.CameraReady(caps, true), guidance(HintKind.READY, enabled = true))
        assertThat(state.location).isEqualTo(LocationChipUi.Ready(4))
    }
}
