package com.clearcapture.clearcapture.domain.session

import com.clearcapture.clearcapture.domain.model.PendingCapture
import java.util.concurrent.ConcurrentHashMap

/** Photographs taken in this flow and waiting for a decision, by capture ID. Flow-scoped, in memory. */
class PendingCaptures {
    private val map = ConcurrentHashMap<String, PendingCapture>()

    /** Remembers [capture]. */
    fun put(capture: PendingCapture) {
        map[capture.captureId] = capture
    }

    /** The capture with [captureId], if still pending. */
    operator fun get(captureId: String): PendingCapture? = map[captureId]

    /** Forgets [captureId]. */
    fun remove(captureId: String) {
        map.remove(captureId)
    }
}
