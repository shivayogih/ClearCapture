package com.clearcapture.clearcapture.data.record

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clearcapture.clearcapture.domain.model.CaptureRecord
import com.clearcapture.clearcapture.domain.model.CaptureStatus
import com.clearcapture.clearcapture.domain.model.CheckResult
import com.clearcapture.clearcapture.domain.model.DeviceInfo
import com.clearcapture.clearcapture.domain.model.ImageInfo
import com.clearcapture.clearcapture.domain.model.LocationFix
import com.clearcapture.clearcapture.domain.model.QualityCheck
import com.clearcapture.clearcapture.domain.model.QualityReport
import com.clearcapture.clearcapture.domain.model.ReasonCode
import com.clearcapture.clearcapture.domain.model.Stage
import com.clearcapture.clearcapture.domain.model.Verdict
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureDaoTest {

    private lateinit var db: CaptureDatabase
    private lateinit var repository: RoomCaptureRepository

    private fun record(id: String, at: Long, status: CaptureStatus = CaptureStatus.PENDING) = CaptureRecord(
        captureId = id,
        requestId = "r",
        profileId = "container",
        status = status,
        report = QualityReport(
            Stage.POST_CAPTURE,
            listOf(CheckResult(QualityCheck.BLUR, Verdict.WARNING, ReasonCode.BLUR_FOCUS, mapOf("lapVarRegion" to 150.0), mapOf("blurWarn" to 180.0))),
            Verdict.WARNING, 85, 640,
        ),
        overrideReason = null,
        filePath = "/p/$id.jpg",
        image = ImageInfo(4000, 3000, 2_000_000, 1, null),
        fix = LocationFix(12.97, 77.59, 920.0, 4.2f, at - 500, "gps", false),
        capturedAtEpochMillis = at,
        shutterMillis = 420,
        device = DeviceInfo("Tab", "0", 35),
        profileSchemaVersion = 1,
    )

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, CaptureDatabase::class.java).build()
        repository = RoomCaptureRepository({ db }, Dispatchers.IO)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun savesAndReadsBackEveryField(): Unit = runBlocking {
        val original = record("a", 1_000)
        repository.saveAttempt(original)
        assertThat(repository.observeAll().first().single()).isEqualTo(original.copy(image = original.image?.copy(captureTimeEpochMillis = null)))
    }

    @Test
    fun ordersNewestFirst(): Unit = runBlocking {
        repository.saveAttempt(record("old", 1_000))
        repository.saveAttempt(record("new", 2_000))
        assertThat(repository.observeAll().first().map { it.captureId }).containsExactly("new", "old").inOrder()
    }

    @Test
    fun marksAcceptedAndDiscarded(): Unit = runBlocking {
        repository.saveAttempt(record("a", 1_000))
        repository.saveAttempt(record("b", 1_000))
        repository.markAccepted("a", "/final/a.jpg", "Override reason here")
        repository.markDiscarded("b")
        val byId = repository.observeAll().first().associateBy { it.captureId }
        assertThat(byId.getValue("a").status).isEqualTo(CaptureStatus.ACCEPTED)
        assertThat(byId.getValue("a").filePath).isEqualTo("/final/a.jpg")
        assertThat(byId.getValue("a").overrideReason).isEqualTo("Override reason here")
        assertThat(byId.getValue("b").status).isEqualTo(CaptureStatus.DISCARDED)
    }

    @Test
    fun deletesAndPurges(): Unit = runBlocking {
        repository.saveAttempt(record("a", 1_000))
        repository.saveAttempt(record("b", 2_000))
        repository.saveAttempt(record("c", 3_000))
        repository.delete("a")
        repository.purgeOlderThan(2_500)
        assertThat(repository.observeAll().first().map { it.captureId }).containsExactly("c")
    }

    @Test
    fun failedAttemptWithoutReportRoundTrips(): Unit = runBlocking {
        val failed = record("f", 1_000, CaptureStatus.FAILED).copy(report = null, image = null, filePath = null)
        repository.saveAttempt(failed)
        assertThat(repository.observeAll().first().single()).isEqualTo(failed)
    }
}
