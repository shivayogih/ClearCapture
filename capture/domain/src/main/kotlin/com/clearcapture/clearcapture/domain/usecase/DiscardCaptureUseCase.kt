package com.clearcapture.clearcapture.domain.usecase

import com.clearcapture.clearcapture.domain.model.PendingCapture
import com.clearcapture.clearcapture.domain.port.CaptureRepository
import com.clearcapture.clearcapture.domain.port.FileStore
import com.clearcapture.clearcapture.domain.session.PendingCaptures

/** Retake: deletes the pending file and keeps the record, marked discarded, for calibration. */
class DiscardCaptureUseCase(
    private val fileStore: FileStore,
    private val repository: CaptureRepository,
    private val pending: PendingCaptures,
) {
    suspend operator fun invoke(capture: PendingCapture) {
        fileStore.delete(capture.file)
        repository.markDiscarded(capture.captureId)
        pending.remove(capture.captureId)
    }
}
