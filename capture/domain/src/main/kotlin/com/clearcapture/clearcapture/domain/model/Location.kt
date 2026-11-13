package com.clearcapture.clearcapture.domain.model

/** A location fix, without Android types. */
data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val accuracyMeters: Float,
    val fixTimeEpochMillis: Long,
    val provider: String,
    val isMock: Boolean,
)

/** State of the location source during a capture session. */
sealed interface LocationStatus {
    /** Location permission is not granted. */
    data object PermissionMissing : LocationStatus

    /** Location services are switched off. */
    data object ProviderDisabled : LocationStatus

    /** Waiting for a first usable fix. */
    data object Acquiring : LocationStatus

    /** A fix within the profile's age and accuracy limits. */
    data class Ready(val fix: LocationFix) : LocationStatus

    /** The latest fix is older than the profile allows. */
    data class Stale(val fix: LocationFix) : LocationStatus

    /** The latest fix is less accurate than the profile allows. */
    data class Inaccurate(val fix: LocationFix) : LocationStatus

    /** Location is not needed by the active profile. */
    data object NotRequired : LocationStatus
}

/** The fix carried by this status, if any. */
val LocationStatus.fixOrNull: LocationFix?
    get() = when (this) {
        is LocationStatus.Ready -> fix
        is LocationStatus.Stale -> fix
        is LocationStatus.Inaccurate -> fix
        else -> null
    }
