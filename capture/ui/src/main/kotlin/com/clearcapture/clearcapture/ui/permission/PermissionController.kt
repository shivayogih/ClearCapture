package com.clearcapture.clearcapture.ui.permission

import com.clearcapture.clearcapture.ui.permission.PermissionReducer.withStep
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/**
 * The readiness permissions of a capture session: state, intents in, effects out. A plain class owned by the
 * session's ViewModel; [snapshot] reads the system state so this stays free of Android types.
 *
 * @param locationRequired false when the session never uses location (LocationMode.OFF or such profiles).
 * @param locationOptional true for LocationMode.OPTIONAL: asked for once, never blocks.
 */
class PermissionController(
    locationRequired: Boolean,
    locationOptional: Boolean,
    private val snapshot: () -> PermissionSnapshot,
) {
    private val mutableState = MutableStateFlow(
        PermissionUiState(
            location = if (locationRequired || locationOptional) Grant.NOT_ASKED else null,
            locationOptional = locationOptional,
        ).withStep(),
    )

    /** Current permission state. */
    val state: StateFlow<PermissionUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<PermissionEffect>(Channel.BUFFERED)

    /** One-off effects: system requests, settings screens, and Granted once everything needed is in place. */
    val effects: Flow<PermissionEffect> = effectChannel.receiveAsFlow()

    private var grantedSent = false

    init {
        recheck()
    }

    /** True once everything the session needs is granted (or optional and answered). */
    val isReady: Boolean get() = state.value.step == PermissionStep.Done

    /** Handles one intent. */
    fun onIntent(intent: PermissionIntent) {
        when (intent) {
            PermissionIntent.Recheck -> recheck()
            PermissionIntent.PrimaryAction -> PermissionReducer.primaryEffect(state.value)?.let(effectChannel::trySend)
            is PermissionIntent.CardAction -> PermissionReducer.cardEffect(state.value, intent.kind)?.let(effectChannel::trySend)
            is PermissionIntent.RequestResult -> {
                mutableState.update { PermissionReducer.onRequestResult(it, intent.kinds, snapshot(), intent.rationale) }
                notifyIfDone()
            }
            PermissionIntent.Leave -> effectChannel.trySend(PermissionEffect.Cancelled)
        }
    }

    private fun recheck() {
        mutableState.update { PermissionReducer.onSnapshot(it, snapshot()) }
        // A permission revoked in settings makes the session ask again.
        if (!isReady) grantedSent = false
        notifyIfDone()
    }

    private fun notifyIfDone() {
        if (isReady && !grantedSent) {
            grantedSent = true
            effectChannel.trySend(PermissionEffect.Granted)
        }
    }
}
