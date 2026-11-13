package com.clearcapture.clearcapture.api

/** Something that happened in the capture flow, for host analytics. Never contains coordinates or images. */
public sealed interface CaptureEvent {
    /** The request this event belongs to. */
    public val requestId: String

    /** The flow opened. */
    public data class FlowOpened(override val requestId: String, val profileId: String?) : CaptureEvent

    /** The permission step finished; [granted] is true when everything the profile needs was granted. */
    public data class PermissionResult(override val requestId: String, val granted: Boolean) : CaptureEvent

    /** A photograph was taken. */
    public data class CaptureTaken(override val requestId: String, val captureId: String) : CaptureEvent

    /** A verdict was shown. [verdict] is `PASS`, `WARNING` or `FAIL`. */
    public data class Verdict(override val requestId: String, val captureId: String, val verdict: String) : CaptureEvent

    /** The flow closed with [result]. */
    public data class FlowClosed(override val requestId: String, val result: CaptureResult) : CaptureEvent
}

/** Receives [CaptureEvent]s on the main thread. Implementations must return quickly. */
public fun interface CaptureEventListener {
    /** Called for each event. */
    public fun onEvent(event: CaptureEvent)
}
