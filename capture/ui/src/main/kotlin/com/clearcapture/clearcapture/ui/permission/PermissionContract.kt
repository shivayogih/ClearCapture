package com.clearcapture.clearcapture.ui.permission

/** A permission the flow asks for. */
enum class PermissionKind {
    CAMERA,
    LOCATION,
    ;

    /** The Android permissions requested together for this kind (fine and coarse go together on Android 12+). */
    val manifestPermissions: List<String>
        get() = when (this) {
            CAMERA -> listOf(PermissionNames.CAMERA)
            LOCATION -> listOf(PermissionNames.FINE_LOCATION, PermissionNames.COARSE_LOCATION)
        }
}

/** Android permission names, kept here so the pure logic needs no Android types. */
object PermissionNames {
    const val CAMERA: String = "android.permission.CAMERA"
    const val FINE_LOCATION: String = "android.permission.ACCESS_FINE_LOCATION"
    const val COARSE_LOCATION: String = "android.permission.ACCESS_COARSE_LOCATION"
}

/** State of one permission as the flow understands it. */
enum class Grant {
    GRANTED,

    /** Not asked yet in this run. */
    NOT_ASKED,

    /** Refused once; the system will still show its dialog. */
    DENIED,

    /** Refused for good, or the system no longer shows its dialog; only app settings can fix it. */
    PERMANENTLY_DENIED,

    /** Location only: approximate granted, precise not. */
    APPROXIMATE_ONLY,

    /** Location only: the user refused the upgrade to precise; only app settings can fix it. */
    PRECISE_REFUSED,
}

/** What the system reports right now. Read on every recheck. */
data class PermissionSnapshot(
    val camera: Boolean,
    val fineLocation: Boolean,
    val coarseLocation: Boolean,
    val locationServicesOn: Boolean,
)

/** The one thing the permission screen shows at a time. */
sealed interface PermissionStep {
    /** Explain [kind] before the system dialog. */
    data class Explain(val kind: PermissionKind) : PermissionStep

    /** [kind] was refused once; explain what will not work and offer to ask again. */
    data class Denied(val kind: PermissionKind) : PermissionStep

    /** [kind] is refused for good; offer the app's system settings. */
    data class PermanentlyDenied(val kind: PermissionKind) : PermissionStep

    /** Only approximate location; ask for the upgrade to precise. */
    data object PreciseNeeded : PermissionStep

    /** The user refused precise location; offer the app's system settings. */
    data object PreciseRefused : PermissionStep

    /** Location services are off; offer the location settings. */
    data object LocationServicesOff : PermissionStep

    /** Everything needed is in place. */
    data object Done : PermissionStep
}

/** State of the permission screen. */
data class PermissionUiState(
    val camera: Grant = Grant.NOT_ASKED,
    /** `null` when the profile does not need location; it is then never requested. */
    val location: Grant? = Grant.NOT_ASKED,
    val locationServicesOn: Boolean = true,
    val step: PermissionStep = PermissionStep.Explain(PermissionKind.CAMERA),
)

/** What the user or the system did. */
sealed interface PermissionIntent {
    /** Re-read the system state, for example after returning from settings. */
    data object Recheck : PermissionIntent

    /** The primary action of the current step (ask, ask again, open settings). */
    data object PrimaryAction : PermissionIntent

    /** The system dialog for [kind] closed. [rationale] is `shouldShowRequestPermissionRationale` per permission. */
    data class RequestResult(val kind: PermissionKind, val rationale: Map<String, Boolean>) : PermissionIntent

    /** The user left the permission step. */
    data object Leave : PermissionIntent
}

/** One-off effects of the permission screen. */
sealed interface PermissionEffect {
    /** Show the system dialog for [permissions]. */
    data class LaunchRequest(val kind: PermissionKind, val permissions: List<String>) : PermissionEffect

    /** Open this app's page in system settings. */
    data object OpenAppSettings : PermissionEffect

    /** Open the system location settings. */
    data object OpenLocationSettings : PermissionEffect

    /** Everything is granted; continue the flow. */
    data object Granted : PermissionEffect

    /** The user left; the flow ends with `Cancelled`. */
    data object Cancelled : PermissionEffect
}
