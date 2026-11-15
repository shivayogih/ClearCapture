package com.clearcapture.clearcapture.api

/** What [ClearCapture.checkReadiness] found. */
public class Readiness(
    /** The blockers, in a stable order; empty when ready. */
    public val blockers: List<Blocker>,
) {
    /** True when nothing blocks a capture. */
    public val isReady: Boolean get() = blockers.isEmpty()

    /** Blockers that cannot be fixed inside the flow, so a launch fails at once in either permission mode. */
    public val fatalBlockers: List<Blocker> get() = blockers.filter { it.isFatal }

    override fun toString(): String = "Readiness(isReady=$isReady, blockers=$blockers)"
}

/** One reason a capture cannot happen yet. */
public enum class Blocker(
    /** True when neither the user nor the SDK can fix it during the flow. */
    public val isFatal: Boolean,
) {
    /** No back camera. */
    NO_CAMERA_HARDWARE(isFatal = true),

    /** A device policy disables the camera. */
    CAMERA_DISABLED_BY_POLICY(isFatal = true),

    /** Camera permission is not granted. */
    CAMERA_PERMISSION_MISSING(isFatal = false),

    /** Neither fine nor coarse location is granted. */
    LOCATION_PERMISSION_MISSING(isFatal = false),

    /** Only approximate location is granted. */
    LOCATION_PRECISE_NOT_GRANTED(isFatal = false),

    /** Location services are switched off. */
    LOCATION_SERVICES_OFF(isFatal = false),

    /** The device has no usable location provider. */
    NO_LOCATION_PROVIDER(isFatal = true),

    /** Less than about 50 MB free. */
    LOW_STORAGE(isFatal = true),

    /** The SDK has no native library for this processor. */
    UNSUPPORTED_ABI(isFatal = true),

    /** The requested profile ID is unknown. */
    PROFILE_NOT_FOUND(isFatal = true),

    /** The host's manifest removed a permission the SDK declares. */
    PERMISSION_REMOVED_FROM_MANIFEST(isFatal = true),
}
