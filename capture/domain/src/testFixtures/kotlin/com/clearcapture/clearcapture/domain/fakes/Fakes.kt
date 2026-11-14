package com.clearcapture.clearcapture.domain.fakes

import com.clearcapture.clearcapture.common.Clock
import com.clearcapture.clearcapture.common.Outcome
import com.clearcapture.clearcapture.domain.model.CameraState
import com.clearcapture.clearcapture.domain.model.CaptureError
import com.clearcapture.clearcapture.domain.model.CaptureProfile
import com.clearcapture.clearcapture.domain.model.CaptureRecord
import com.clearcapture.clearcapture.domain.model.CaptureStatus
import com.clearcapture.clearcapture.domain.model.FrameData
import com.clearcapture.clearcapture.domain.model.ImageInfo
import com.clearcapture.clearcapture.domain.model.LocationFix
import com.clearcapture.clearcapture.domain.model.LocationPolicy
import com.clearcapture.clearcapture.domain.model.LocationStatus
import com.clearcapture.clearcapture.domain.model.QualityReport
import com.clearcapture.clearcapture.domain.model.Stage
import com.clearcapture.clearcapture.domain.model.Verdict
import com.clearcapture.clearcapture.domain.port.CameraController
import com.clearcapture.clearcapture.domain.port.CaptureRepository
import com.clearcapture.clearcapture.domain.port.ExifStore
import com.clearcapture.clearcapture.domain.port.FileStore
import com.clearcapture.clearcapture.domain.port.LocationSource
import com.clearcapture.clearcapture.domain.port.QualityAnalyzer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import java.io.File

/** A clock tests move by hand. */
class FakeClock(var now: Long = 1_000_000L) : Clock {
    override fun nowMillis(): Long = now
    override fun elapsedNanos(): Long = now * 1_000_000L

    /** Moves time forward. */
    fun advance(millis: Long) {
        now += millis
    }
}

/** A location source whose status tests set directly. */
class FakeLocationSource(initial: LocationStatus = LocationStatus.Acquiring) : LocationSource {
    val state = MutableStateFlow(initial)
    var startedWith: LocationPolicy? = null
    var stopped = false
    override val status = state
    override fun start(policy: LocationPolicy) {
        startedWith = policy
        stopped = false
    }
    override fun stop() {
        stopped = true
    }
    override fun current(): LocationStatus = state.value
}

/** A camera that writes a small file, or fails with [failWith]. */
class FakeCameraController : CameraController {
    val mutableState = MutableStateFlow<CameraState>(CameraState.Idle)
    override val state: StateFlow<CameraState> = mutableState
    var failWith: CaptureError? = null
    var lastLocation: LocationFix? = null
    var torchOn = false
    var focusedAt: Pair<Float, Float>? = null
    override suspend fun takePicture(target: File, location: LocationFix?): Outcome<File, CaptureError> {
        lastLocation = location
        failWith?.let { return Outcome.Failure(it) }
        target.parentFile?.mkdirs()
        target.writeBytes(ByteArray(16))
        return Outcome.Success(target)
    }
    override suspend fun focusAt(x: Float, y: Float) {
        focusedAt = x to y
    }
    override fun setTorch(enabled: Boolean) {
        torchOn = enabled
    }
}

/** Files under a temporary directory. */
class FakeFileStore(private val root: File) : FileStore {
    var free: Long = Long.MAX_VALUE
    var promoteError: CaptureError? = null
    val deleted = mutableListOf<File>()
    override fun newPendingFile(captureId: String) = File(root, "pending/$captureId.jpg")
    override suspend fun promote(pending: File, captureId: String, epochMillis: Long): Outcome<File, CaptureError> {
        promoteError?.let { return Outcome.Failure(it) }
        val target = File(root, "accepted/$captureId.jpg").also { it.parentFile?.mkdirs() }
        pending.renameTo(target)
        return Outcome.Success(target)
    }
    override suspend fun delete(file: File) {
        deleted += file
        file.delete()
    }
    override fun freeBytes(): Long = free
    override suspend fun deleteOrphans(maxAgeMillis: Long) = Unit
}

/** EXIF tags held in memory per file. [cameraWritesTags] mimics CameraX writing GPS tags itself. */
class FakeExifStore(var cameraWritesTags: Boolean = true, var writeSticks: Boolean = true) : ExifStore {
    private val tags = mutableMapOf<String, LocationFix>()
    var writes = 0
    override suspend fun writeLocation(file: File, fix: LocationFix): Outcome<Unit, CaptureError> {
        writes++
        if (writeSticks) tags.getOrPut(file.path) { fix }
        return Outcome.Success(Unit)
    }
    override suspend fun readLocation(file: File): LocationFix? = tags[file.path]
    override suspend fun copyTags(from: File, to: File) {
        tags[from.path]?.let { tags[to.path] = it }
    }
    override suspend fun readImageInfo(file: File) = ImageInfo(4000, 3000, file.length(), 1, null)

    /** Called by tests after the camera "wrote" the file. */
    fun cameraWrote(file: File, fix: LocationFix?) {
        if (cameraWritesTags && fix != null) tags[file.path] = fix
    }
}

/** An analyzer returning [fileReport] for files and [liveReport] for frames. */
class FakeQualityAnalyzer(
    var fileReport: QualityReport = QualityReport(Stage.POST_CAPTURE, emptyList(), Verdict.PASS, 100, 10),
    var liveReport: QualityReport = QualityReport(Stage.LIVE, emptyList(), Verdict.PASS, 100, 5),
) : QualityAnalyzer {
    var warmUpResult: Outcome<Unit, CaptureError> = Outcome.Success(Unit)
    var fileError: CaptureError? = null
    var liveCalls = 0
    override suspend fun warmUp() = warmUpResult
    override fun analyzeLive(frame: FrameData, profile: CaptureProfile): QualityReport = liveReport.also { liveCalls++ }
    override suspend fun analyzeFile(file: File, profile: CaptureProfile, expectedCaptureLongSide: Int?) =
        fileError?.let { Outcome.Failure(it) } ?: Outcome.Success(fileReport)
}

/** Records in memory. */
class FakeCaptureRepository : CaptureRepository {
    val records = MutableStateFlow<Map<String, CaptureRecord>>(emptyMap())
    override suspend fun saveAttempt(record: CaptureRecord) {
        records.value = records.value + (record.captureId to record)
    }
    override suspend fun markAccepted(captureId: String, finalPath: String, overrideReason: String?) = update(captureId) {
        it.copy(status = CaptureStatus.ACCEPTED, filePath = finalPath, overrideReason = overrideReason)
    }
    override suspend fun markDiscarded(captureId: String) = update(captureId) { it.copy(status = CaptureStatus.DISCARDED) }
    override suspend fun delete(captureId: String) {
        records.value = records.value - captureId
    }
    override suspend fun purgeOlderThan(epochMillis: Long) {
        records.value = records.value.filterValues { it.capturedAtEpochMillis >= epochMillis }
    }
    override fun observeAll() = records.map { it.values.sortedByDescending { r -> r.capturedAtEpochMillis } }
    private fun update(id: String, change: (CaptureRecord) -> CaptureRecord) {
        records.value[id]?.let { records.value = records.value + (id to change(it)) }
    }
}
