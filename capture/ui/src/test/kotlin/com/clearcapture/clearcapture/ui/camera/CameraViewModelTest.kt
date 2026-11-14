package com.clearcapture.clearcapture.ui.camera

import app.cash.turbine.test
import com.clearcapture.clearcapture.common.Outcome
import com.clearcapture.clearcapture.domain.fakes.FakeCameraController
import com.clearcapture.clearcapture.domain.hint.GuideStatus
import com.clearcapture.clearcapture.domain.hint.Hint
import com.clearcapture.clearcapture.domain.hint.HintKind
import com.clearcapture.clearcapture.domain.hint.LiveGuidance
import com.clearcapture.clearcapture.domain.model.CameraCapabilities
import com.clearcapture.clearcapture.domain.model.CameraState
import com.clearcapture.clearcapture.domain.model.CaptureError
import com.clearcapture.clearcapture.domain.model.CaptureProfile
import com.clearcapture.clearcapture.domain.model.LocationStatus
import com.clearcapture.clearcapture.domain.model.PendingCapture
import com.clearcapture.clearcapture.domain.model.QualityReport
import com.clearcapture.clearcapture.domain.model.Size
import com.clearcapture.clearcapture.domain.model.Stage
import com.clearcapture.clearcapture.domain.model.TestProfiles
import com.clearcapture.clearcapture.domain.model.Verdict
import com.clearcapture.clearcapture.domain.port.CaptureProfileProvider
import com.clearcapture.clearcapture.domain.usecase.GetProfilesUseCase
import com.clearcapture.clearcapture.ui.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.io.File

class CameraViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val camera = FakeCameraController()
    private val guidance = MutableStateFlow(LiveGuidance(Hint(HintKind.READY, false), GuideStatus.Ready, true, null, LocationStatus.NotRequired))
    private var captureResult: Outcome<PendingCapture, CaptureError> = Outcome.Failure(CaptureError.StorageFull)
    private var expected: Int? = null
    private val activeProfiles = mutableListOf<CaptureProfile?>()
    private var missing = false
    private val caps = CameraCapabilities("0", null, null, Size(4000, 3000), hasFlash = true, hasAutoFocus = true, hardwareLevel = "FULL")

    private val vm by lazy {
        val provider = object : CaptureProfileProvider {
            override suspend fun profiles() = listOf(TestProfiles.container())
            override suspend fun profile(id: String) = profiles().firstOrNull { it.id == id }
        }
        CameraViewModel(
            CameraDependencies(
                getProfiles = GetProfilesUseCase(provider),
                camera = camera,
                guidance = { guidance },
                capture = { _, longSide ->
                    expected = longSide
                    captureResult
                },
                onActiveProfile = { activeProfiles += it },
                onProfileMissing = { missing = true },
            ),
        )
    }

    private fun ready() {
        vm.onIntent(CameraIntent.Start("container"))
        camera.mutableState.value = CameraState.Ready(caps)
    }

    @Test
    fun `start loads the profile, feeds it to live analysis and enables the shutter when ready`() {
        ready()
        assertThat(vm.state.value.profile?.id).isEqualTo("container")
        assertThat(vm.state.value.captureEnabled).isTrue()
        assertThat(activeProfiles.last()?.id).isEqualTo("container")
    }

    @Test
    fun `unknown profile is reported`() {
        vm.onIntent(CameraIntent.Start("spaceship"))
        assertThat(missing).isTrue()
    }

    @Test
    fun `shutter success navigates to review and passes the bound capture size`() = runTest {
        ready()
        val report = QualityReport(Stage.POST_CAPTURE, emptyList(), Verdict.PASS, 100, 1)
        captureResult = Outcome.Success(PendingCapture("c9", File("x"), "container", report, null, 0))
        vm.effects.test {
            vm.onIntent(CameraIntent.ShutterPressed)
            assertThat(awaitItem()).isEqualTo(CameraEffect.HapticShutter)
            assertThat(awaitItem()).isEqualTo(CameraEffect.NavigateToReview("c9"))
        }
        assertThat(expected).isEqualTo(4000)
        assertThat(vm.state.value.isCapturing).isFalse()
    }

    @Test
    fun `shutter failure shows the error`() = runTest {
        ready()
        vm.effects.test {
            vm.onIntent(CameraIntent.ShutterPressed)
            assertThat(awaitItem()).isEqualTo(CameraEffect.HapticShutter)
            assertThat(awaitItem()).isEqualTo(CameraEffect.ShowError(CaptureError.StorageFull))
        }
    }

    @Test
    fun `shutter does nothing while disabled`() = runTest {
        vm.onIntent(CameraIntent.Start("container"))
        vm.effects.test {
            vm.onIntent(CameraIntent.ShutterPressed)
            expectNoEvents()
        }
    }

    @Test
    fun `torch toggles only when available`() {
        ready()
        vm.onIntent(CameraIntent.ToggleTorch)
        assertThat(camera.torchOn).isTrue()
        assertThat(vm.state.value.torchOn).isTrue()
    }

    @Test
    fun `tap to focus reaches the camera`() {
        ready()
        vm.onIntent(CameraIntent.TapToFocus(0.25f, 0.75f))
        assertThat(camera.focusedAt).isEqualTo(0.25f to 0.75f)
    }

    @Test
    fun `starting twice does not restart`() {
        ready()
        vm.onIntent(CameraIntent.Start("container"))
        assertThat(activeProfiles.filterNotNull().map { it.id }.distinct()).containsExactly("container")
    }
}
