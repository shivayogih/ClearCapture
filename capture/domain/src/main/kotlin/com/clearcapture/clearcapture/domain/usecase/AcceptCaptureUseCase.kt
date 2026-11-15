package com.clearcapture.clearcapture.domain.usecase

import com.clearcapture.clearcapture.common.Outcome
import com.clearcapture.clearcapture.domain.model.AcceptedCapture
import com.clearcapture.clearcapture.domain.model.CaptureError
import com.clearcapture.clearcapture.domain.model.PendingCapture
import com.clearcapture.clearcapture.domain.port.CaptureRepository
import com.clearcapture.clearcapture.domain.port.FileStore
import com.clearcapture.clearcapture.domain.session.PendingCaptures

/** Why a photograph could not be accepted. */
sealed interface AcceptError {
    /** The profile's rules do not allow accepting this verdict (or the override reason is too short). */
    data object NotAllowed : AcceptError

    /** Moving the file failed. */
    data class Storage(val error: CaptureError) : AcceptError
}

/** Accepts a pending photograph: checks the rules, promotes the file and marks the record accepted. */
class AcceptCaptureUseCase(
    private val fileStore: FileStore,
    private val repository: CaptureRepository,
    private val pending: PendingCaptures,
) {
    suspend operator fun invoke(
        capture: PendingCapture,
        allowAcceptOnWarning: Boolean,
        allowOverrideOnFail: Boolean,
        overrideReason: String?,
    ): Outcome<AcceptedCapture, AcceptError> {
        val verdict = capture.report.verdict
        if (!AcceptRules.canAccept(verdict, allowAcceptOnWarning, allowOverrideOnFail, overrideReason)) {
            return Outcome.Failure(AcceptError.NotAllowed)
        }
        val reason = overrideReason?.trim()?.takeIf { AcceptRules.needsOverrideReason(verdict, allowOverrideOnFail) }
        return when (val promoted = fileStore.promote(capture.file, capture.captureId, capture.capturedAtEpochMillis)) {
            is Outcome.Failure -> Outcome.Failure(AcceptError.Storage(promoted.error))
            is Outcome.Success -> {
                repository.markAccepted(capture.captureId, promoted.value.path, reason)
                pending.remove(capture.captureId)
                Outcome.Success(
                    AcceptedCapture(
                        captureId = capture.captureId,
                        file = promoted.value,
                        profileId = capture.profileId,
                        report = capture.report,
                        overrideReason = reason,
                        fix = capture.fix,
                        capturedAtEpochMillis = capture.capturedAtEpochMillis,
                    ),
                )
            }
        }
    }
}
