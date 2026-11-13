package com.clearcapture.clearcapture.common

import kotlinx.coroutines.CoroutineDispatcher

/** Dispatchers used by the SDK. Injected everywhere so tests can replace them with test dispatchers. */
data class AppDispatchers(
    /** UI work and state reduction. */
    val main: CoroutineDispatcher,
    /** File, EXIF and database work. */
    val io: CoroutineDispatcher,
    /** CPU work such as post-capture analysis and OpenCV initialisation. */
    val default: CoroutineDispatcher,
)
