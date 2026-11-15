package com.clearcapture.clearcapture.domain.usecase

import com.clearcapture.clearcapture.common.Outcome
import com.clearcapture.clearcapture.domain.fakes.FakeCaptureRepository
import com.clearcapture.clearcapture.domain.fakes.FakeFileStore
import com.clearcapture.clearcapture.domain.model.CaptureError
import com.clearcapture.clearcapture.domain.model.CaptureRecord
import com.clearcapture.clearcapture.domain.model.CaptureStatus
import com.clearcapture.clearcapture.domain.model.DeviceInfo
import com.clearcapture.clearcapture.domain.model.PendingCapture
import com.clearcapture.clearcapture.domain.model.QualityReport
import com.clearcapture.clearcapture.domain.model.Stage
import com.clearcapture.clearcapture.domain.model.Verdict
import com.clearcapture.clearcapture.domain.session.PendingCaptures
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AcceptDiscardUseCaseTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val repository = FakeCaptureRepository()
    private val pending = PendingCaptures()
    private val files by lazy { FakeFileStore(temp.root) }

    private suspend fun capture(verdict: Verdict): PendingCapture {
        val file = files.newPendingFile("c1").also { it.parentFile?.mkdirs(); it.writeBytes(ByteArray(4)) }
        val capture = PendingCapture("c1", file, "container", QualityReport(Stage.POST_CAPTURE, emptyList(), verdict, 60, 1), null, 5)
        pending.put(capture)
        repository.saveAttempt(
            CaptureRecord("c1", "r", "container", CaptureStatus.PENDING, capture.report, null, file.path, null, null, 5, 1, DeviceInfo("m", "0", 1), 1),
        )
        return capture
    }

    @Test
    fun `accept rules`() {
        assertThat(AcceptRules.canAccept(Verdict.PASS, false, false, null)).isTrue()
        assertThat(AcceptRules.canAccept(Verdict.WARNING, true, false, null)).isTrue()
        assertThat(AcceptRules.canAccept(Verdict.WARNING, false, false, null)).isFalse()
        assertThat(AcceptRules.canAccept(Verdict.FAIL, true, false, "long enough reason")).isFalse()
        assertThat(AcceptRules.canAccept(Verdict.FAIL, false, true, "too short")).isFalse()
        assertThat(AcceptRules.canAccept(Verdict.FAIL, false, true, "  ten chars!  ")).isTrue()
    }

    @Test
    fun `accepting promotes the file and marks the record accepted`() = runTest {
        val capture = capture(Verdict.WARNING)
        val accepted = AcceptCaptureUseCase(files, repository, pending)(capture, true, false, null)

        val value = (accepted as Outcome.Success).value
        assertThat(value.file.exists()).isTrue()
        assertThat(value.file.path).contains("accepted")
        assertThat(repository.records.value.getValue("c1").status).isEqualTo(CaptureStatus.ACCEPTED)
        assertThat(pending["c1"]).isNull()
    }

    @Test
    fun `failed photo with override stores the reason`() = runTest {
        val capture = capture(Verdict.FAIL)
        val accepted = AcceptCaptureUseCase(files, repository, pending)(capture, false, true, " Truck left the site ")
        assertThat((accepted as Outcome.Success).value.overrideReason).isEqualTo("Truck left the site")
        assertThat(repository.records.value.getValue("c1").overrideReason).isEqualTo("Truck left the site")
    }

    @Test
    fun `not allowed leaves everything untouched`() = runTest {
        val capture = capture(Verdict.FAIL)
        assertThat(AcceptCaptureUseCase(files, repository, pending)(capture, true, false, null)).isEqualTo(Outcome.Failure(AcceptError.NotAllowed))
        assertThat(repository.records.value.getValue("c1").status).isEqualTo(CaptureStatus.PENDING)
        assertThat(capture.file.exists()).isTrue()
    }

    @Test
    fun `storage error on promotion is reported`() = runTest {
        val capture = capture(Verdict.PASS)
        files.promoteError = CaptureError.StorageFull
        assertThat(AcceptCaptureUseCase(files, repository, pending)(capture, true, false, null))
            .isEqualTo(Outcome.Failure(AcceptError.Storage(CaptureError.StorageFull)))
    }

    @Test
    fun `retake deletes the pending file and keeps the record as discarded`() = runTest {
        val capture = capture(Verdict.FAIL)
        DiscardCaptureUseCase(files, repository, pending)(capture)
        assertThat(capture.file.exists()).isFalse()
        assertThat(repository.records.value.getValue("c1").status).isEqualTo(CaptureStatus.DISCARDED)
        assertThat(pending["c1"]).isNull()
    }
}
