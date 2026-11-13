package com.clearcapture.clearcapture.ui.permission

import com.clearcapture.clearcapture.ui.mvi.MviViewModel
import com.clearcapture.clearcapture.ui.permission.PermissionReducer.withStep

/**
 * Drives the permission step. [snapshot] reads the system state (permissions and location services);
 * it is a function so the ViewModel stays free of Android types.
 */
class PermissionViewModel(
    locationRequired: Boolean,
    private val snapshot: () -> PermissionSnapshot,
) : MviViewModel<PermissionUiState, PermissionIntent, PermissionEffect>(
    PermissionUiState(location = if (locationRequired) Grant.NOT_ASKED else null).withStep(),
) {
    private var grantedSent = false

    init {
        recheck()
    }

    override fun onIntent(intent: PermissionIntent) {
        when (intent) {
            PermissionIntent.Recheck -> recheck()
            PermissionIntent.PrimaryAction -> PermissionReducer.primaryEffect(state.value)?.let(::emit)
            is PermissionIntent.RequestResult -> {
                setState { PermissionReducer.onRequestResult(it, intent.kind, snapshot(), intent.rationale) }
                notifyIfDone()
            }
            PermissionIntent.Leave -> emit(PermissionEffect.Cancelled)
        }
    }

    private fun recheck() {
        setState { PermissionReducer.onSnapshot(it, snapshot()) }
        notifyIfDone()
    }

    private fun notifyIfDone() {
        if (state.value.step == PermissionStep.Done && !grantedSent) {
            grantedSent = true
            emit(PermissionEffect.Granted)
        }
    }
}
