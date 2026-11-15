package com.clearcapture.clearcapture.domain.usecase

import com.clearcapture.clearcapture.common.Outcome
import com.clearcapture.clearcapture.domain.fakes.FakeCaptureRepository
import com.clearcapture.clearcapture.domain.fakes.FakeDestination
import com.clearcapture.clearcapture.domain.fakes.FakeExifStore
import com.clearcapture.clearcapture.domain.fakes.FakeFileStore
import com.clearcapture.clearcapture.domain.model.CaptureRecord
import com.clearcapture.clearcapture.domain.model.CaptureStatus
import com.clearcapture.clearcapture.domain.model.DeviceInfo
import com.clearcapture.clearcapture.domain.model.LocationNote
import com.clearcapture.clearcapture.domain.model.PendingCapture
import com.clearcapture.clearcapture.domain.model.QualityReport
import com.clearcapture.clearcapture.domain.model.Stage
import com.clearcapture.clearcapture.domain.model.Verdict
import com.clearcapture.clearcapture.domain.port.StoreFailure
import com.clearcapture.clearcapture.domain.session.PendingCaptures
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KeepDiscardUseCaseTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val repository = FakeCaptureRepository()
    private val pending = PendingCaptures()
    private val exif = FakeExifStore()
    private val files by lazy { FakeFileStore(temp.root) }
    private val host by lazy { FakeDestination(temp.root, "host") }
    private val sdk by lazy { FakeDestination(temp.root, "sdk") }
    private val refs = mapOf("jobId" to "4711")

    private fun keep(destination: FakeDestination = host) =
        KeepCaptureUseCase(destination, sdk, exif, repository, pending, refs) { "hash-of-" + it.name }

    private suspend fun capture(verdict: Verdict): PendingCapture {
        val file = files.newPendingFile("c1").also { it.parentFile?.mkdirs(); it.writeBytes(ByteArray(4)) }
        val capture = PendingCapture(
            "c1", file, "container", QualityReport(Stage.POST_CAPTURE, emptyList(), verdict, 60, 1), null, 5,
            label = "Bin front", locationNote = LocationNote.NOT_REQUESTED,
        )
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
    fun `keeping writes references, stores at the host destination, hashes and marks accepted`() = runTest {
        val capture = capture(Verdict.WARNING)
        val kept = (keep()(capture, true, false, null) as Outcome.Success).value

        assertThat(exif.references[capture.file.path]).isEqualTo(refs)
        assertThat(kept.uri).contains("/host/c1.jpg")
        assertThat(kept.sha256).isEqualTo("hash-of-c1.jpg")
        assertThat(kept.label).isEqualTo("Bin front")
        assertThat(kept.locationNote).isEqualTo(LocationNote.NOT_REQUESTED)
        assertThat(kept.deliveryNote).isNull()
        assertThat(repository.records.value.getValue("c1").status).isEqualTo(CaptureStatus.ACCEPTED)
        assertThat(pending["c1"]).isNull()
    }

    @Test
    fun `a failing host destination falls back to SDK storage and says why`() = runTest {
        val capture = capture(Verdict.PASS)
        host.failWith = StoreFailure("Folder is not writable", outOfSpace = false)
        val kept = (keep()(capture, true, false, null) as Outcome.Success).value
        assertThat(kept.uri).contains("/sdk/c1.jpg")
        assertThat(kept.deliveryNote).isEqualTo("STORED_IN_FALLBACK: Folder is not writable")
    }

    @Test
    fun `when both fail the photo stays pending and nothing is lost`() = runTest {
        val capture = capture(Verdict.PASS)
        host.failWith = StoreFailure("Folder is not writable", outOfSpace = false)
        sdk.failWith = StoreFailure("No space left", outOfSpace = true)
        val result = keep()(capture, true, false, null)
        assertThat(result).isEqualTo(Outcome.Failure(KeepError.Storage(StoreFailure("No space left", outOfSpace = true))))
        assertThat(capture.file.exists()).isTrue()
        assertThat(pending["c1"]).isNotNull()
        assertThat(repository.records.value.getValue("c1").status).isEqualTo(CaptureStatus.PENDING)
    }

    @Test
    fun `failed photo with override stores the reason`() = runTest {
        val capture = capture(Verdict.FAIL)
        val kept = (keep()(capture, false, true, " Truck left the site ") as Outcome.Success).value
        assertThat(kept.overrideReason).isEqualTo("Truck left the site")
    }

    @Test
    fun `not allowed leaves everything untouched`() = runTest {
        val capture = capture(Verdict.FAIL)
        assertThat(keep()(capture, true, false, null)).isEqualTo(Outcome.Failure(KeepError.NotAllowed))
        assertThat(capture.file.exists()).isTrue()
        assertThat(exif.references).isEmpty()
    }

    @Test
    fun `retake deletes the pending file and keeps the record as discarded`() = runTest {
        val capture = capture(Verdict.FAIL)
        DiscardCaptureUseCase(files, repository, pending)(capture)
        assertThat(capture.file.exists()).isFalse()
        assertThat(repository.records.value.getValue("c1").status).isEqualTo(CaptureStatus.DISCARDED)
    }
}
