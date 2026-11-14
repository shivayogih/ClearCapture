package com.clearcapture.clearcapture.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow

/** Collects one-off effects while the screen is at least started, so none is lost or replayed on rotation. */
@Composable
internal fun <E> CollectEffects(effects: Flow<E>, onEffect: (E) -> Unit) {
    val owner = LocalLifecycleOwner.current
    val current by rememberUpdatedState(onEffect)
    LaunchedEffect(effects, owner) {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) { effects.collect { current(it) } }
    }
}
