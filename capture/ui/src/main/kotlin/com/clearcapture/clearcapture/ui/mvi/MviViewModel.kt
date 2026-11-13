package com.clearcapture.clearcapture.ui.mvi

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/**
 * Base for every screen's ViewModel: one immutable [state], intents in through [onIntent],
 * one-off [effects] out through a channel so they are not replayed after rotation.
 */
abstract class MviViewModel<S, I, E>(initial: S) : ViewModel() {

    private val mutableState = MutableStateFlow(initial)

    /** The single source of truth for the screen. */
    val state: StateFlow<S> = mutableState.asStateFlow()

    private val effectChannel = Channel<E>(Channel.BUFFERED)

    /** One-off events: navigation, haptics, system screens. */
    val effects: Flow<E> = effectChannel.receiveAsFlow()

    /** Handles one user intent. */
    abstract fun onIntent(intent: I)

    /** Applies a pure state change. */
    protected fun setState(reduce: (S) -> S) {
        mutableState.update(reduce)
    }

    /** Sends a one-off effect. */
    protected fun emit(effect: E) {
        effectChannel.trySend(effect)
    }
}
