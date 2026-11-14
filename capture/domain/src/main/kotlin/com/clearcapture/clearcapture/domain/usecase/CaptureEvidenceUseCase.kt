package com.clearcapture.clearcapture.domain.usecase

import com.clearcapture.clearcapture.common.Clock
import com.clearcapture.clearcapture.common.Outcome
import com.clearcapture.clearcapture.domain.location.LocationFixPolicy
import com.clearcapture.clearcapture.domain.model.CaptureError
import com.clearcapture.clearcapture.domain.model.CaptureProfile
import com.clearcapture.clearcapture.domain.model.CaptureRecord
import com.clearcapture.clearcapture.domain.model.CaptureStatus
import com.clearcapture.clearcapture.domain.model.DeviceInfo
import com.clearcapture.clearcapture.domain.model.LocationFix
import com.clearcapture.clearcapture.domain.model.LocationStatus
import com.clearcapture.clearcapture.domain.model.PendingCapture
import com.clearcapture.clearcapture.domain.model.QualityCheck
import com.clearcapture.clearcapture.domain.model.QualityReport
import com.clearcapture.clearcapture.domain.model.Stage
import com.clearcapture.clearcapture.domain.model.fixOrNull
import com.clearcapture.clearcapture.domain.port.CameraController
import com.clearcapture.clearcapture.domain.port.CaptureRepository
import com.clearcapture.clearcapture.domain.port.ExifStore
import com.clearcapture.clearcapture.domain.port.FileStore
import com.clearcapture.clearcapture.domain.port.LocationSource
import com.clearcapture.clearcapture.domain.port.QualityAnalyzer
import com.clearcapture.clearcapture.domain.quality.QualityScoreCalculator
import com.clearcapture.clearcapture.domain.session.PendingCaptures

/** Takes a photograph, verifies its location tags, checks its quality and records the attempt (LLD 3.3). */
class CaptureEvidenceUseCase(
    private val locationSource: LocationSource,
    private val fileStore: FileStore,
    private val camera: CameraController,
    private val exifStore: ExifStore,
    private val analyzer: QualityAnalyzer,
    private val repository: CaptureRepository,
    private val pending: PendingCaptures,
    private val clock: Clock,
    private val newId: () -> String,
    private val device: () -> DeviceInfo,
    private val requestId: String,
) {
    /**
     * @param profile the resolved profile.
     * @param expectedCaptureLongSide what the camera reported at bind time, to detect unexpected downscaling.
     */
    suspend operator fun invoke(profile: CaptureProfile, expectedCaptureLongSide: Int?): Outcome<PendingCapture, CaptureError> {
        val status = locationSource.current()
        return when (val blocked = gate(profile, status)) {
            null -> capture(profile, status.fixOrNull, expectedCaptureLongSide)
            else -> Outcome.Failure(blocked)
        }
    }

    /** Steps 1 and 2: what stops the shutter before anything is written. */
    private fun gate(profile: CaptureProfile, status: LocationStatus): CaptureError? =
        LocationFixPolicy.blockReason(status, profile.location)?.let(CaptureError::LocationBlocked)
            ?: CaptureError.StorageFull.takeIf { fileStore.freeBytes() < MIN_FREE_BYTES }

    /** Steps 3 to 9. */
    private suspend fun capture(
        profile: CaptureProfile,
        fix: LocationFix?,
        expectedCaptureLongSide: Int?,
    ): Outcome<PendingCapture, CaptureError> {
        val id = newId()
        val file = fileStore.newPendingFile(id)
        val shutterStart = clock.elapsedNanos()
        // Step 3: take the photograph with the fix as EXIF metadata.
        val saved = camera.takePicture(file, fix)
        val shutterMillis = (clock.elapsedNanos() - shutterStart) / NANOS_PER_MILLI
        val capturedAt = clock.nowMillis()
        if (saved is Outcome.Failure) {
            recordFailure(id, profile, fix, capturedAt, shutterMillis)
            return saved
        }

        // Steps 4 and 5: complete and verify the GPS tags, then judge the fix.
        val locationResult = if (profile.location.required) {
            fix?.let { exifStore.writeLocation(file, it) }
            val tagsPresent = exifStore.readLocation(file) != null
            LocationFixPolicy.checkResult(fix, profile.location, capturedAt, tagsPresent)
        } else {
            null
        }

        // Step 6: quality checks on the saved file.
        val analysed = analyzer.analyzeFile(file, profile, expectedCaptureLongSide)
        if (analysed is Outcome.Failure) {
            recordFailure(id, profile, fix, capturedAt, shutterMillis)
            return analysed
        }
        val quality = (analysed as Outcome.Success).value

        // Step 7: append the location result and recalculate.
        val results = quality.results.filter { it.check != QualityCheck.LOCATION } + listOfNotNull(locationResult)
        val report = QualityScoreCalculator.combine(Stage.POST_CAPTURE, results, profile, quality.analysisMillis)

        // Step 8: record the attempt.
        repository.saveAttempt(record(id, profile, CaptureStatus.PENDING, report, file.path, fix, capturedAt, shutterMillis, file))

        // Step 9.
        return Outcome.Success(PendingCapture(id, file, profile.id, report, fix, capturedAt).also(pending::put))
    }

    private suspend fun recordFailure(id: String, profile: CaptureProfile, fix: LocationFix?, at: Long, shutterMillis: Long) {
        repository.saveAttempt(record(id, profile, CaptureStatus.FAILED, null, null, fix, at, shutterMillis, null))
    }

    @Suppress("LongParameterList") // One record field each; a builder would only move the list.
    private suspend fun record(
        id: String,
        profile: CaptureProfile,
        status: CaptureStatus,
        report: QualityReport?,
        path: String?,
        fix: LocationFix?,
        at: Long,
        shutterMillis: Long,
        file: java.io.File?,
    ) = CaptureRecord(
        captureId = id,
        requestId = requestId,
        profileId = profile.id,
        status = status,
        report = report,
        overrideReason = null,
        filePath = path,
        image = file?.let { exifStore.readImageInfo(it) },
        fix = fix,
        capturedAtEpochMillis = at,
        shutterMillis = shutterMillis,
        device = device(),
        profileSchemaVersion = profile.schemaVersion,
    )

    private companion object {
        const val MIN_FREE_BYTES = 50L * 1024 * 1024
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
