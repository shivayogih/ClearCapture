package com.clearcapture.clearcapture.domain.usecase

import com.clearcapture.clearcapture.common.Outcome
import com.clearcapture.clearcapture.domain.fakes.FakeCameraController
import com.clearcapture.clearcapture.domain.fakes.FakeCaptureRepository
import com.clearcapture.clearcapture.domain.fakes.FakeClock
import com.clearcapture.clearcapture.domain.fakes.FakeExifStore
import com.clearcapture.clearcapture.domain.fakes.FakeFileStore
import com.clearcapture.clearcapture.domain.fakes.FakeLocationSource
import com.clearcapture.clearcapture.domain.fakes.FakeQualityAnalyzer
import com.clearcapture.clearcapture.domain.model.CaptureError
import com.clearcapture.clearcapture.domain.model.CaptureStatus
import com.clearcapture.clearcapture.domain.model.DeviceInfo
import com.clearcapture.clearcapture.domain.model.LocationFix
import com.clearcapture.clearcapture.domain.model.LocationStatus
import com.clearcapture.clearcapture.domain.model.QualityCheck
import com.clearcapture.clearcapture.domain.model.QualityReport
import com.clearcapture.clearcapture.domain.model.ReasonCode
import com.clearcapture.clearcapture.domain.model.Stage
import com.clearcapture.clearcapture.domain.model.TestProfiles
import com.clearcapture.clearcapture.domain.model.Verdict
import com.clearcapture.clearcapture.domain.result
import com.clearcapture.clearcapture.domain.session.PendingCaptures
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CaptureEvidenceUseCaseTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val clock = FakeClock()
    private val fix = LocationFix(51.5, -0.1, 20.0, 4f, clock.now - 1_000, "gps", false)
    private val location = FakeLocationSource(LocationStatus.Ready(fix))
    private val camera = FakeCameraController()
    private val exif = FakeExifStore()
    private val analyzer = FakeQualityAnalyzer(
        fileReport = QualityReport(Stage.POST_CAPTURE, listOf(result(QualityCheck.BLUR, Verdict.PASS)), Verdict.PASS, 100, 20),
    )
    private val repository = FakeCaptureRepository()
    private val pending = PendingCaptures()
    private val files by lazy { FakeFileStore(temp.root) }
    private val profile = TestProfiles.container()

    private val useCase by lazy {
        CaptureEvidenceUseCase(
            location, files, camera, exif, analyzer, repository, pending, clock,
            newId = { "c1" }, device = { DeviceInfo("Tab", "0", 35) }, requestId = "r1",
        )
    }

    @Test
    fun `happy path passes, writes the accuracy tag, records a pending attempt`() = runTest {
        val outcome = useCase(profile, expectedCaptureLongSide = 4000)

        val capture = (outcome as Outcome.Success).value
        assertThat(capture.report.verdict).isEqualTo(Verdict.PASS)
        assertThat(capture.report.resultOf(QualityCheck.LOCATION)?.verdict).isEqualTo(Verdict.PASS)
        assertThat(camera.lastLocation).isEqualTo(fix)
        assertThat(exif.writes).isEqualTo(1)
        assertThat(pending["c1"]).isEqualTo(capture)
        val record = repository.records.value.getValue("c1")
        assertThat(record.status).isEqualTo(CaptureStatus.PENDING)
        assertThat(record.fix).isEqualTo(fix)
        assertThat(record.requestId).isEqualTo("r1")
    }

    @Test
    fun `blocked without permission, with location off, and while acquiring`() = runTest {
        val cases = mapOf(
            LocationStatus.PermissionMissing to ReasonCode.LOCATION_PERMISSION,
            LocationStatus.ProviderDisabled to ReasonCode.LOCATION_DISABLED,
            LocationStatus.Acquiring to ReasonCode.LOCATION_NO_FIX,
        )
        for ((status, reason) in cases) {
            location.state.value = status
            assertThat(useCase(profile, null)).isEqualTo(Outcome.Failure(CaptureError.LocationBlocked(reason)))
        }
        assertThat(repository.records.value).isEmpty()
    }

    @Test
    fun `stale fix is a warning unless the profile fails on it`() = runTest {
        location.state.value = LocationStatus.Stale(fix)
        clock.advance(120_000)
        val warned = (useCase(profile, null) as Outcome.Success).value
        assertThat(warned.report.resultOf(QualityCheck.LOCATION)?.reason).isEqualTo(ReasonCode.LOCATION_STALE)
        assertThat(warned.report.verdict).isEqualTo(Verdict.WARNING)

        val strict = profile.copy(location = profile.location.copy(failOnStale = true))
        assertThat(useCase(strict, null)).isEqualTo(Outcome.Failure(CaptureError.LocationBlocked(ReasonCode.LOCATION_STALE)))
    }

    @Test
    fun `missing gps tags after saving fail the image`() = runTest {
        exif.writeSticks = false
        val capture = (useCase(profile, null) as Outcome.Success).value
        assertThat(capture.report.resultOf(QualityCheck.LOCATION)?.reason).isEqualTo(ReasonCode.LOCATION_TAGS_MISSING)
        assertThat(capture.report.verdict).isEqualTo(Verdict.FAIL)
    }

    @Test
    fun `mock fix is flagged`() = runTest {
        location.state.value = LocationStatus.Ready(fix.copy(isMock = true))
        val capture = (useCase(profile, null) as Outcome.Success).value
        assertThat(capture.report.resultOf(QualityCheck.LOCATION)?.reason).isEqualTo(ReasonCode.LOCATION_MOCK)
    }

    @Test
    fun `storage full stops before the shutter`() = runTest {
        files.free = 10
        assertThat(useCase(profile, null)).isEqualTo(Outcome.Failure(CaptureError.StorageFull))
        assertThat(camera.lastLocation).isNull()
    }

    @Test
    fun `camera failure is recorded as a failed attempt`() = runTest {
        camera.failWith = CaptureError.CaptureFailed("boom")
        assertThat(useCase(profile, null)).isEqualTo(Outcome.Failure(CaptureError.CaptureFailed("boom")))
        assertThat(repository.records.value.getValue("c1").status).isEqualTo(CaptureStatus.FAILED)
    }

    @Test
    fun `unreadable file is recorded as a failed attempt`() = runTest {
        analyzer.fileError = CaptureError.FileUnreadable("c1.jpg")
        assertThat(useCase(profile, null)).isEqualTo(Outcome.Failure(CaptureError.FileUnreadable("c1.jpg")))
        assertThat(repository.records.value.getValue("c1").status).isEqualTo(CaptureStatus.FAILED)
    }

    @Test
    fun `profile without location skips location entirely`() = runTest {
        location.state.value = LocationStatus.NotRequired
        val noLocation = profile.copy(location = profile.location.copy(required = false), requiredChecks = profile.requiredChecks - QualityCheck.LOCATION)
        val capture = (useCase(noLocation, null) as Outcome.Success).value
        assertThat(capture.report.resultOf(QualityCheck.LOCATION)).isNull()
        assertThat(exif.writes).isEqualTo(0)
    }
}
