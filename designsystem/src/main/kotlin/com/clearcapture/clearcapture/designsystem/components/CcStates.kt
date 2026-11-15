package com.clearcapture.clearcapture.designsystem.components

import androidx.compose.runtime.Immutable

/**
 * Status of the live guidance on the camera screen (DESIGN_SYSTEM.md section 2.4). Maps one to one
 * to `GuideStatus` in the LLD.
 */
public enum class CcStatus {
    /** Aiming with no stable result yet, or waiting for a location fix. */
    Neutral,

    /** The photo can be taken. */
    Ready,

    /** A warning-level hint; the shutter stays enabled. */
    Warning,

    /** A blocking hint; the shutter is disabled. */
    Problem,
}

/** State of [CcShutterButton]. */
public enum class CcShutterState {
    /** The photo can be taken. */
    Enabled,

    /** The photo cannot be taken; a reason is read out. */
    Disabled,

    /** A capture is in progress; a progress ring is shown. */
    Capturing,
}

/** Shape of the framing guide drawn by [CcGuideOverlay]. */
public enum class CcGuideShape {
    /** No guide is drawn. */
    NONE,

    /** Four corner brackets around a container or other box-shaped subject. */
    CONTAINER,

    /** Corner brackets with a head-and-shoulders outline. */
    PORTRAIT,

    /** Corner brackets with a side-on vehicle outline. */
    VEHICLE,
}

/**
 * The guide rectangle as fractions of the overlay's width and height, each in 0..1, so the guide
 * keeps its place in portrait, landscape and any aspect ratio.
 *
 * @property left Left edge, as a fraction of the width.
 * @property top Top edge, as a fraction of the height.
 * @property right Right edge, as a fraction of the width; greater than [left].
 * @property bottom Bottom edge, as a fraction of the height; greater than [top].
 */
@Immutable
public data class CcGuideRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(left in 0f..1f && top in 0f..1f && right in 0f..1f && bottom in 0f..1f) {
            "Guide fractions must be in 0..1: $this"
        }
        require(right > left && bottom > top) { "Guide rectangle must not be empty: $this" }
    }

    /** Factory functions. */
    public companion object {
        /** A rectangle centred in the overlay, [width] and [height] given as fractions. */
        public fun centered(width: Float, height: Float): CcGuideRect {
            val horizontal = (1f - width) / 2f
            val vertical = (1f - height) / 2f
            return CcGuideRect(horizontal, vertical, horizontal + width, vertical + height)
        }
    }
}

/** State of [CcLocationChip]. Maps one to one to `LocationChipUi` in the LLD. */
@Immutable
public sealed interface CcLocationState {
    /** Waiting for a first fix. */
    public data object Acquiring : CcLocationState

    /**
     * A fix good enough to capture.
     *
     * @property accuracyMeters Horizontal accuracy, rounded to whole metres.
     */
    public data class Ready(val accuracyMeters: Int) : CcLocationState

    /**
     * A fix too inaccurate or too old for the profile.
     *
     * @property accuracyMeters Horizontal accuracy in whole metres, if known.
     */
    public data class Weak(val accuracyMeters: Int?) : CcLocationState

    /** Location services are off; tapping the chip can open the location settings. */
    public data object Off : CcLocationState

    /** The location permission is missing; tapping the chip can open the app settings. */
    public data object NoPermission : CcLocationState
}

/** Overall result shown by [CcVerdictBadge]. Maps one to one to `VerdictUi` in the LLD. */
public enum class CcVerdict {
    /** Every required check passed. */
    Pass,

    /** At least one check gave a warning and none failed. */
    Warning,

    /** At least one required check failed. */
    Fail,

    /** The photo is still being checked. */
    Loading,
}

/** Result of one quality check, shown by [CcCheckRow]. */
public enum class CcCheckStatus {
    /** The check passed. */
    Pass,

    /** The check gave a warning. */
    Warning,

    /** The check failed. */
    Fail,
}

/** State of the permission explained by [CcPermissionCard]. */
public enum class CcPermissionState {
    /** Not asked yet. */
    Default,

    /** Granted. */
    Granted,

    /** Denied; it can be asked again. */
    Denied,

    /** Denied permanently; it can only be allowed in the system settings. */
    PermanentlyDenied,
}

/** Kind of full-screen state shown by [CcStatusScreen]. */
public enum class CcStatusKind {
    /** Information, such as a state the user can resolve. */
    Info,

    /** An error, such as the camera being unavailable or storage being full. */
    Error,
}

/** Where a [CcIconButton] is drawn. */
public enum class CcIconButtonStyle {
    /** Over the camera preview: on a scrim. */
    Camera,

    /** On a normal screen surface. */
    Surface,
}
