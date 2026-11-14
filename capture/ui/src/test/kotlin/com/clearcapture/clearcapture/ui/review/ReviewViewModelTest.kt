package com.clearcapture.clearcapture.ui.review

import app.cash.turbine.test
import com.clearcapture.clearcapture.domain.fakes.FakeCaptureRepository
import com.clearcapture.clearcapture.domain.fakes.FakeFileStore
import com.clearcapture.clearcapture.domain.model.CheckResult
import com.clearcapture.clearcapture.domain.model.FlowOutcome
import com.clearcapture.clearcapture.domain.model.LocationFix
import com.clearcapture.clearcapture.domain.model.PendingCapture
import com.clearcapture.clearcapture.domain.model.QualityCheck
import com.clearcapture.clearcapture.domain.model.QualityReport
import com.clearcapture.clearcapture.domain.model.ReasonCode
import com.clearcapture.clearcapture.domain.model.Stage
import com.clearcapture.clearcapture.domain.model.Verdict
import com.clearcapture.clearcapture.domain.session.PendingCaptures
import com.clearcapture.clearcapture.domain.usecase.AcceptCaptureUseCase
import com.clearcapture.clearcapture.domain.usecase.DiscardCaptureUseCase
import com.clearcapture.clearcapture.ui.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReviewViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val temp = TemporaryFolder()

    private val pending = PendingCaptures()
    private val repository = FakeCaptureRepository()
    private val files by lazy { FakeFileStore(temp.root) }
    private var policy = AcceptPolicy(allowAcceptOnWarning = true, allowOverrideOnFail = false)

    private fun vm() = ReviewViewModel(
        pending, AcceptCaptureUseCase(files, repository, pending), DiscardCaptureUseCase(files, repository, pending),
    ) { policy }

    private fun pendingCapture(verdict: Verdict): PendingCapture {
        val file = files.newPendingFile("c1").also { it.parentFile?.mkdirs(); it.writeBytes(ByteArray(8)) }
        val results = listOf(CheckResult(QualityCheck.BLUR, verdict, if (verdict == Verdict.PASS) null else ReasonCode.BLUR_MOTION, emptyMap(), emptyMap()))
        return PendingCapture("c1", file, "container", QualityReport(Stage.POST_CAPTURE, results, verdict, 60, 1),
            LocationFix(1.0, 2.0, null, 4.4f, 0, "gps", false), 0).also(pending::put)
    }

    @Test
    fun `pass loads with accept allowed and a haptic`() = runTest {
        pendingCapture(Verdict.PASS)
        val vm = vm()
        vm.effects.test {
            vm.onIntent(ReviewIntent.Load("c1"))
            assertThat(awaitItem()).isEqualTo(ReviewEffect.HapticVerdict(Verdict.PASS))
        }
        assertThat(vm.state.value.canAccept).isTrue()
        assertThat(vm.state.value.location).isEqualTo(LocationSummaryUi(4, false))
        assertThat(vm.state.value.checks.single().check).isEqualTo(QualityCheck.BLUR)
    }

    @Test
    fun `warning follows the accept-on-warning rule`() {
        pendingCapture(Verdict.WARNING)
        policy = policy.copy(allowAcceptOnWarning = false)
        val vm = vm().apply { onIntent(ReviewIntent.Load("c1")) }
        assertThat(vm.state.value.canAccept).isFalse()
        assertThat(vm.state.value.acceptBlock).isEqualTo(AcceptBlock.WARNING_NOT_ALLOWED)
    }

    @Test
    fun `fail without override cannot be accepted`() {
        pendingCapture(Verdict.FAIL)
        val vm = vm().apply { onIntent(ReviewIntent.Load("c1")) }
        assertThat(vm.state.value.canAccept).isFalse()
        assertThat(vm.state.value.needsOverrideReason).isFalse()
        assertThat(vm.state.value.acceptBlock).isEqualTo(AcceptBlock.FAIL_NOT_ALLOWED)
    }

    @Test
    fun `fail with override needs a reason of ten characters`() = runTest {
        pendingCapture(Verdict.FAIL)
        policy = policy.copy(allowOverrideOnFail = true)
        val vm = vm().apply { onIntent(ReviewIntent.Load("c1")) }
        assertThat(vm.state.value.needsOverrideReason).isTrue()
        assertThat(vm.state.value.acceptBlock).isEqualTo(AcceptBlock.REASON_TOO_SHORT)
        vm.onIntent(ReviewIntent.OverrideReasonChanged("Truck moved away"))
        assertThat(vm.state.value.canAccept).isTrue()
        vm.effects.test {
            assertThat(awaitItem()).isInstanceOf(ReviewEffect.HapticVerdict::class.java)
            vm.onIntent(ReviewIntent.Accept)
            val finish = awaitItem() as ReviewEffect.Finish
            assertThat((finish.outcome as FlowOutcome.Accepted).capture.overrideReason).isEqualTo("Truck moved away")
        }
    }

    @Test
    fun `accept finishes the flow with the accepted capture`() = runTest {
        pendingCapture(Verdict.PASS)
        val vm = vm().apply { onIntent(ReviewIntent.Load("c1")) }
        vm.effects.test {
            assertThat(awaitItem()).isInstanceOf(ReviewEffect.HapticVerdict::class.java)
            vm.onIntent(ReviewIntent.Accept)
            val finish = awaitItem() as ReviewEffect.Finish
            assertThat((finish.outcome as FlowOutcome.Accepted).capture.file.exists()).isTrue()
        }
    }

    @Test
    fun `retake deletes the pending file and returns to the camera`() = runTest {
        val capture = pendingCapture(Verdict.FAIL)
        val vm = vm().apply { onIntent(ReviewIntent.Load("c1")) }
        vm.effects.test {
            assertThat(awaitItem()).isInstanceOf(ReviewEffect.HapticVerdict::class.java)
            vm.onIntent(ReviewIntent.Retake)
            assertThat(awaitItem()).isEqualTo(ReviewEffect.BackToCamera)
        }
        assertThat(capture.file.exists()).isFalse()
    }

    @Test
    fun `missing capture returns to the camera`() = runTest {
        val vm = vm()
        vm.effects.test {
            vm.onIntent(ReviewIntent.Load("gone"))
            assertThat(awaitItem()).isEqualTo(ReviewEffect.BackToCamera)
        }
    }
}
