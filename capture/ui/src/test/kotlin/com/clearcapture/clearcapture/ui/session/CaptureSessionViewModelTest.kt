package com.clearcapture.clearcapture.ui.session

import app.cash.turbine.test
import com.clearcapture.clearcapture.common.Outcome
import com.clearcapture.clearcapture.domain.fakes.FakeCameraController
import com.clearcapture.clearcapture.domain.hint.GuideStatus
import com.clearcapture.clearcapture.domain.hint.Hint
import com.clearcapture.clearcapture.domain.hint.HintKind
import com.clearcapture.clearcapture.domain.hint.LiveGuidance
import com.clearcapture.clearcapture.domain.model.AcceptedCapture
import com.clearcapture.clearcapture.domain.model.CameraCapabilities
import com.clearcapture.clearcapture.domain.model.CameraState
import com.clearcapture.clearcapture.domain.model.CaptureError
import com.clearcapture.clearcapture.domain.model.FailureCode
import com.clearcapture.clearcapture.domain.model.FlowOutcome
import com.clearcapture.clearcapture.domain.model.LocationStatus
import com.clearcapture.clearcapture.domain.model.PendingCapture
import com.clearcapture.clearcapture.domain.model.QualityReport
import com.clearcapture.clearcapture.domain.model.SessionPlan
import com.clearcapture.clearcapture.domain.model.ShotPlan
import com.clearcapture.clearcapture.domain.model.Size
import com.clearcapture.clearcapture.domain.model.Stage
import com.clearcapture.clearcapture.domain.model.TestProfiles
import com.clearcapture.clearcapture.domain.model.Verdict
import com.clearcapture.clearcapture.domain.port.StoreFailure
import com.clearcapture.clearcapture.domain.usecase.KeepError
import com.clearcapture.clearcapture.ui.MainDispatcherRule
import com.clearcapture.clearcapture.ui.permission.PermissionController
import com.clearcapture.clearcapture.ui.permission.PermissionSnapshot
import com.clearcapture.clearcapture.ui.review.AcceptPolicy
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.io.File

class CaptureSessionViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val camera = FakeCameraController()
    private val caps = CameraCapabilities("0", null, null, Size(4000, 3000), hasFlash = true, hasAutoFocus = true, hardwareLevel = "FULL")
    private val ready = LiveGuidance(Hint(HintKind.READY, false), GuideStatus.Ready, true, null, LocationStatus.NotRequired)
    private val vehicle = TestProfiles.container().copy(id = "vehicle", titleKey = "cc_profile_vehicle")
    private var plan = SessionPlan(emptyList(), "container", allowSubjectChange = true, minPhotos = 1, maxPhotos = 3, null, emptyMap())
    private var verdict = Verdict.PASS
    private var captureError: CaptureError? = null
    private var keepError: KeepError? = null
    private var free = Long.MAX_VALUE
    private val captured = mutableListOf<String>()
    private val discarded = mutableListOf<String>()
    private val removed = mutableListOf<String>()
    private var restored = emptyList<AcceptedCapture>()
    private var ids = 0

    private fun pending(profileId: String, label: String?) = PendingCapture(
        "c${++ids}", File("p$ids.jpg"), profileId, QualityReport(Stage.POST_CAPTURE, emptyList(), verdict, 60, 1), null, 0, label,
    )

    private fun accepted(p: PendingCapture, note: String? = null) = AcceptedCapture(
        p.captureId, null, "file:///x/${p.captureId}.jpg", p.profileId, p.report, null, null, 0, p.label, deliveryNote = note,
    )

    private var fallbackNote: String? = null

    private val vm by lazy {
        CaptureSessionViewModel(
            SessionDependencies(
                plan = plan,
                profiles = { listOf(TestProfiles.container(), TestProfiles.general(), vehicle) },
                camera = camera,
                permissions = PermissionController(locationRequired = false, locationOptional = false) {
                    PermissionSnapshot(camera = true, fineLocation = true, coarseLocation = true, locationServicesOn = true)
                },
                guidance = { MutableStateFlow(ready) },
                capture = { profile, _, _, label ->
                    captured += profile.id
                    captureError?.let { Outcome.Failure(it) } ?: Outcome.Success(pending(profile.id, label))
                },
                keep = { p, _, _ -> keepError?.let { Outcome.Failure(it) } ?: Outcome.Success(accepted(p, fallbackNote)) },
                discard = { discarded += it.captureId },
                removeKept = { removed += it.captureId },
                restoreKept = { restored },
                acceptPolicy = { AcceptPolicy(allowAcceptOnWarning = true, allowOverrideOnFail = false) },
                freeBytes = { free },
                onActiveProfile = {},
                onReady = {},
            ),
        )
    }

    private fun readyCamera() {
        vm.state.value
        camera.mutableState.value = CameraState.Ready(caps)
    }

    @Test
    fun `a passing photo is kept automatically and the counter advances`() = runTest {
        readyCamera()
        vm.effects.test {
            vm.onIntent(SessionIntent.Shutter)
            assertThat(awaitItem()).isEqualTo(SessionEffect.HapticShutter)
            assertThat(awaitItem()).isEqualTo(SessionEffect.HapticVerdict(Verdict.PASS))
            assertThat(awaitItem()).isEqualTo(SessionEffect.PhotoKept(1))
        }
        assertThat(vm.state.value.kept.map { it.captureId }).containsExactly("c1")
        assertThat(vm.state.value.photoNumber).isEqualTo(2)
        assertThat(vm.state.value.canFinish).isTrue()
    }

    @Test
    fun `a warning opens the panel - keep adds the photo and retake discards it`() = runTest {
        verdict = Verdict.WARNING
        readyCamera()
        vm.onIntent(SessionIntent.Shutter)
        assertThat(vm.state.value.panel).isNotNull()
        assertThat(vm.state.value.shutterEnabled).isFalse()
        vm.onIntent(SessionIntent.Retake)
        assertThat(vm.state.value.panel).isNull()
        assertThat(discarded).containsExactly("c1")

        vm.onIntent(SessionIntent.Shutter)
        vm.onIntent(SessionIntent.Keep)
        assertThat(vm.state.value.kept.map { it.captureId }).containsExactly("c2")
    }

    @Test
    fun `planned shots set the subject and label of each photo`() = runTest {
        plan = plan.copy(shots = listOf(ShotPlan("container", "Bin front"), ShotPlan("vehicle", "Truck")), minPhotos = 2, maxPhotos = 3)
        readyCamera()
        assertThat(vm.state.value.subjectId).isEqualTo("container")
        assertThat(vm.state.value.label).isEqualTo("Bin front")
        vm.onIntent(SessionIntent.Shutter)
        assertThat(vm.state.value.subjectId).isEqualTo("vehicle")
        assertThat(vm.state.value.label).isEqualTo("Truck")
        vm.onIntent(SessionIntent.Shutter)
        assertThat(vm.state.value.subjectId).isEqualTo("container")
        assertThat(vm.state.value.label).isNull()
        assertThat(captured).containsExactly("container", "vehicle").inOrder()
    }

    @Test
    fun `the worker can switch subject when allowed, and not when not`() = runTest {
        readyCamera()
        vm.onIntent(SessionIntent.SelectSubject("general"))
        assertThat(vm.state.value.subjectId).isEqualTo("general")
        assertThat(vm.state.value.camera.profile?.id).isEqualTo("general")
    }

    @Test
    fun `switching is refused when the host forbids it`() = runTest {
        plan = plan.copy(allowSubjectChange = false)
        readyCamera()
        vm.onIntent(SessionIntent.SelectSubject("general"))
        assertThat(vm.state.value.subjectId).isEqualTo("container")
    }

    @Test
    fun `reaching the maximum finishes with every kept photo`() = runTest {
        plan = plan.copy(minPhotos = 1, maxPhotos = 2)
        readyCamera()
        vm.effects.test {
            vm.onIntent(SessionIntent.Shutter)
            vm.onIntent(SessionIntent.Shutter)
            val finish = cancelAndConsumeRemainingEvents().map { (it as app.cash.turbine.Event.Item).value }.last()
            assertThat((finish as SessionEffect.Finish).outcome).isInstanceOf(FlowOutcome.Completed::class.java)
            assertThat((finish.outcome as FlowOutcome.Completed).captures.map { it.captureId }).containsExactly("c1", "c2").inOrder()
        }
    }

    @Test
    fun `finish is allowed only once the minimum is kept`() = runTest {
        plan = plan.copy(minPhotos = 2, maxPhotos = 3)
        readyCamera()
        vm.onIntent(SessionIntent.Shutter)
        assertThat(vm.state.value.canFinish).isFalse()
        vm.effects.test {
            skipItems(3) // shutter, verdict and kept effects of the first photo
            vm.onIntent(SessionIntent.Finish)
            expectNoEvents()
            vm.onIntent(SessionIntent.Shutter)
            skipItems(3)
            vm.onIntent(SessionIntent.Finish)
            val outcome = (awaitItem() as SessionEffect.Finish).outcome as FlowOutcome.Completed
            assertThat(outcome.captures.map { it.captureId }).containsExactly("c1", "c2").inOrder()
        }
    }

    @Test
    fun `leaving returns the photos kept so far and discards the one in the panel`() = runTest {
        readyCamera()
        vm.onIntent(SessionIntent.Shutter)
        verdict = Verdict.FAIL
        vm.onIntent(SessionIntent.Shutter)
        vm.effects.test {
            vm.onIntent(SessionIntent.Leave)
            val outcome = (expectMostRecentItem() as SessionEffect.Finish).outcome as FlowOutcome.Cancelled
            assertThat(outcome.captures.map { it.captureId }).containsExactly("c1")
        }
        assertThat(discarded).containsExactly("c2")
    }

    @Test
    fun `an unknown planned profile fails the session`() = runTest {
        plan = plan.copy(shots = listOf(ShotPlan("spaceship", null)))
        vm.effects.test {
            val outcome = (awaitItem() as SessionEffect.Finish).outcome as FlowOutcome.Failed
            assertThat(outcome.code).isEqualTo(FailureCode.PROFILE_NOT_FOUND)
        }
    }

    @Test
    fun `storage below the hard limit blocks the shutter with an alert - low storage warns`() = runTest {
        free = CaptureSessionViewModel.FULL_STORAGE_BYTES - 1
        readyCamera()
        assertThat(vm.state.value.alert).isEqualTo(SessionAlert.StorageFull)
        assertThat(vm.state.value.shutterEnabled).isFalse()
        free = CaptureSessionViewModel.LOW_STORAGE_BYTES - 1
        vm.onIntent(SessionIntent.Resume)
        assertThat(vm.state.value.alert).isNull()
        assertThat(vm.state.value.storageLow).isTrue()
        assertThat(vm.state.value.shutterEnabled).isTrue()
    }

    @Test
    fun `a store failure keeps the photo pending, alerts, and can be retried`() = runTest {
        readyCamera()
        keepError = KeepError.Storage(StoreFailure("No space left", outOfSpace = true))
        vm.onIntent(SessionIntent.Shutter)
        assertThat(vm.state.value.alert).isEqualTo(SessionAlert.StoreFailed("No space left", outOfSpace = true))
        assertThat(vm.state.value.kept).isEmpty()
        keepError = null
        vm.onIntent(SessionIntent.RetryStore)
        assertThat(vm.state.value.kept.map { it.captureId }).containsExactly("c1")
        assertThat(vm.state.value.alert).isNull()
    }

    @Test
    fun `a photo stored in the fallback tells the worker why`() = runTest {
        fallbackNote = "STORED_IN_FALLBACK: Folder is not writable"
        readyCamera()
        vm.onIntent(SessionIntent.Shutter)
        assertThat(vm.state.value.alert).isEqualTo(SessionAlert.StoredInFallback("STORED_IN_FALLBACK: Folder is not writable"))
        assertThat(vm.state.value.kept).hasSize(1)
    }

    @Test
    fun `a capture error is shown, a location block is left to the hint`() = runTest {
        readyCamera()
        captureError = CaptureError.CameraInUse
        vm.onIntent(SessionIntent.Shutter)
        assertThat(vm.state.value.alert).isEqualTo(SessionAlert.CaptureFailed(CaptureError.CameraInUse))
    }

    @Test
    fun `photos kept before the process died are restored and count`() = runTest {
        restored = listOf(accepted(pending("container", null)))
        plan = plan.copy(minPhotos = 1, maxPhotos = 3)
        readyCamera()
        assertThat(vm.state.value.kept).hasSize(1)
        assertThat(vm.state.value.photoNumber).isEqualTo(2)
        assertThat(vm.state.value.canFinish).isTrue()
    }

    @Test
    fun `a kept photo can be removed before finishing`() = runTest {
        readyCamera()
        vm.onIntent(SessionIntent.Shutter)
        vm.onIntent(SessionIntent.RemovePhoto("c1"))
        assertThat(vm.state.value.kept).isEmpty()
        assertThat(removed).containsExactly("c1")
    }
}
