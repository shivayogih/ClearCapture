package com.clearcapture.clearcapture.common

/** Wall-clock and monotonic time, injected so time-dependent rules can be tested with a fake. */
interface Clock {
    /** Milliseconds since the Unix epoch. */
    fun nowMillis(): Long

    /** Monotonic nanoseconds, for measuring durations. */
    fun elapsedNanos(): Long

    /** The real system clock. */
    object System : Clock {
        override fun nowMillis(): Long = java.lang.System.currentTimeMillis()
        override fun elapsedNanos(): Long = java.lang.System.nanoTime()
    }
}
