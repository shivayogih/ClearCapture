package com.clearcapture.clearcapture.domain.usecase

import com.clearcapture.clearcapture.common.Outcome
import com.clearcapture.clearcapture.domain.model.AcceptedCapture
import com.clearcapture.clearcapture.domain.model.PendingCapture
import com.clearcapture.clearcapture.domain.port.CaptureRepository
import com.clearcapture.clearcapture.domain.port.ExifStore
import com.clearcapture.clearcapture.domain.port.ImageDestination
import com.clearcapture.clearcapture.domain.port.StoreFailure
import com.clearcapture.clearcapture.domain.port.StoredImage
import com.clearcapture.clearcapture.domain.session.PendingCaptures
import java.io.File

/** Why a photograph could not be kept. */
sealed interface KeepError {
    /** The profile's rules do not allow keeping this verdict (or the override reason is too short). */
    data object NotAllowed : KeepError

    /** Neither the host's destination nor the SDK's fallback could store it. Nothing was lost: it is still pending. */
    data class Storage(val failure: StoreFailure) : KeepError
}

/**
 * Keeps a pending photograph (SDK_REQUIREMENTS 7.1b, 7.1c): checks the rules, writes the host's references into
 * the JPEG, stores it at the host's [destination] or, if that fails, at [fallback], hashes the stored bytes and marks
 * the record accepted.
 */
class KeepCaptureUseCase(
    private val destination: ImageDestination,
    private val fallback: ImageDestination,
    private val exifStore: ExifStore,
    private val repository: CaptureRepository,
    private val pending: PendingCaptures,
    private val references: Map<String, String>,
    private val sha256: suspend (File) -> String,
) {
    suspend operator fun invoke(
        capture: PendingCapture,
        allowAcceptOnWarning: Boolean,
        allowOverrideOnFail: Boolean,
        overrideReason: String?,
    ): Outcome<AcceptedCapture, KeepError> {
        val verdict = capture.report.verdict
        if (!AcceptRules.canAccept(verdict, allowAcceptOnWarning, allowOverrideOnFail, overrideReason)) {
            return Outcome.Failure(KeepError.NotAllowed)
        }
        if (references.isNotEmpty()) exifStore.writeReferences(capture.file, references)
        // Hash and measure before the file moves: a media-store or sink destination may not give a File back.
        val hash = sha256(capture.file)
        val info = exifStore.readImageInfo(capture.file)
        val stored = store(capture)
        return when (stored) {
            is Outcome.Failure -> Outcome.Failure(KeepError.Storage(stored.error))
            is Outcome.Success -> {
                val (image, note) = stored.value
                val reason = overrideReason?.trim()?.takeIf { AcceptRules.needsOverrideReason(verdict, allowOverrideOnFail) }
                repository.markAccepted(capture.captureId, image.uri, reason)
                pending.remove(capture.captureId)
                Outcome.Success(
                    AcceptedCapture(
                        captureId = capture.captureId,
                        file = image.file,
                        uri = image.uri,
                        profileId = capture.profileId,
                        report = capture.report,
                        overrideReason = reason,
                        fix = capture.fix,
                        capturedAtEpochMillis = capture.capturedAtEpochMillis,
                        label = capture.label,
                        locationNote = capture.locationNote,
                        sha256 = hash,
                        width = info?.width ?: 0,
                        height = info?.height ?: 0,
                        fileBytes = info?.fileBytes ?: 0,
                        gpsTime = capture.fix != null,
                        deliveryNote = note,
                    ),
                )
            }
        }
    }

    /** The host's destination first; on failure the SDK's own storage, with a note saying why. */
    private suspend fun store(capture: PendingCapture): Outcome<Pair<StoredImage, String?>, StoreFailure> {
        val primary = destination.store(capture.file, capture.captureId, capture.capturedAtEpochMillis)
        if (primary is Outcome.Success) return Outcome.Success(primary.value to null)
        val primaryFailure = (primary as Outcome.Failure).error
        if (destination === fallback) return Outcome.Failure(primaryFailure)
        return when (val second = fallback.store(capture.file, capture.captureId, capture.capturedAtEpochMillis)) {
            is Outcome.Success -> Outcome.Success(second.value to "STORED_IN_FALLBACK: ${primaryFailure.reason}")
            is Outcome.Failure -> Outcome.Failure(second.error)
        }
    }
}
