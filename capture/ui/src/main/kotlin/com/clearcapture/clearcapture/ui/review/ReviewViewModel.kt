package com.clearcapture.clearcapture.ui.review

import androidx.lifecycle.viewModelScope
import com.clearcapture.clearcapture.common.Outcome
import com.clearcapture.clearcapture.domain.model.FlowOutcome
import com.clearcapture.clearcapture.domain.model.PendingCapture
import com.clearcapture.clearcapture.domain.session.PendingCaptures
import com.clearcapture.clearcapture.domain.usecase.AcceptCaptureUseCase
import com.clearcapture.clearcapture.domain.usecase.DiscardCaptureUseCase
import com.clearcapture.clearcapture.ui.mvi.MviViewModel
import kotlinx.coroutines.launch

/** Shows the verdict of a pending photograph and accepts or discards it. */
class ReviewViewModel(
    private val pending: PendingCaptures,
    private val accept: AcceptCaptureUseCase,
    private val discard: DiscardCaptureUseCase,
    private val policyFor: (PendingCapture) -> AcceptPolicy,
) : MviViewModel<ReviewUiState, ReviewIntent, ReviewEffect>(ReviewUiState()) {

    private var capture: PendingCapture? = null

    override fun onIntent(intent: ReviewIntent) {
        when (intent) {
            is ReviewIntent.Load -> load(intent.captureId)
            ReviewIntent.Accept -> acceptCapture()
            ReviewIntent.Retake -> retake()
            is ReviewIntent.OverrideReasonChanged -> capture?.let { c ->
                setState { ReviewReducer.reasonChanged(it, intent.text, c.report.verdict, policyFor(c)) }
            }
        }
    }

    private fun load(captureId: String) {
        if (capture?.captureId == captureId) return
        val found = pending[captureId]
        if (found == null) {
            // The pending photo is gone (for example after process death); return to the camera to take another.
            emit(ReviewEffect.BackToCamera)
            return
        }
        capture = found
        setState { ReviewReducer.loaded(it, found, policyFor(found)) }
        emit(ReviewEffect.HapticVerdict(found.report.verdict))
    }

    private fun acceptCapture() {
        val current = capture ?: return
        if (!state.value.canAccept || state.value.isSaving) return
        setState { it.copy(isSaving = true, saveFailed = false) }
        viewModelScope.launch {
            val policy = policyFor(current)
            val reason = state.value.overrideReason.takeIf { state.value.needsOverrideReason }
            when (val result = accept(current, policy.allowAcceptOnWarning, policy.allowOverrideOnFail, reason)) {
                is Outcome.Success -> emit(ReviewEffect.Finish(FlowOutcome.Accepted(result.value)))
                is Outcome.Failure -> setState { it.copy(isSaving = false, saveFailed = true) }
            }
        }
    }

    private fun retake() {
        val current = capture ?: return emit(ReviewEffect.BackToCamera)
        viewModelScope.launch {
            discard(current)
            emit(ReviewEffect.BackToCamera)
        }
    }
}
