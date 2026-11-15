package com.clearcapture.clearcapture.api.internal.readiness

import com.clearcapture.clearcapture.api.Blocker
import com.clearcapture.clearcapture.api.Readiness
import com.clearcapture.clearcapture.domain.model.CaptureProfile

/**
 * Works out what blocks a capture. Pure apart from the reads of [environment] and [profiles];
 * shows nothing and changes nothing.
 */
internal class ReadinessChecker(
    private val environment: ReadinessEnvironment,
    private val profiles: () -> List<CaptureProfile>,
) {

    /** Readiness for one profile, or for any profile the worker may pick when [profileId] is `null`. */
    fun check(profileId: String?): Readiness {
        val all = profiles()
        val profile = profileId?.let { id -> all.firstOrNull { it.id == id } }
        // Without a chosen profile the user may pick any, so location is needed if any profile needs it.
        val locationRequired = profile?.location?.required ?: all.any { it.location.required }
        return check(setOfNotNull(profileId), locationRequired)
    }

    /**
     * Readiness for a session that uses [profileIds] (unknown ones are [Blocker.PROFILE_NOT_FOUND]). Location blockers
     * are reported only when [locationRequired]: an optional or switched-off location never blocks.
     */
    fun check(profileIds: Set<String>, locationRequired: Boolean): Readiness {
        val blockers = sortedSetOf<Blocker>()
        if (!environment.declaredPermissions().containsAll(SDK_PERMISSIONS)) blockers += Blocker.PERMISSION_REMOVED_FROM_MANIFEST

        if (!environment.hasBackCamera()) {
            blockers += Blocker.NO_CAMERA_HARDWARE
        } else if (environment.cameraDisabledByPolicy()) {
            blockers += Blocker.CAMERA_DISABLED_BY_POLICY
        }
        if (!environment.isGranted(CAMERA)) blockers += Blocker.CAMERA_PERMISSION_MISSING

        val known = profiles().map { it.id }.toSet()
        if (!known.containsAll(profileIds)) blockers += Blocker.PROFILE_NOT_FOUND
        if (locationRequired) blockers += locationBlockers()

        if (environment.freeBytes() < MIN_FREE_BYTES) blockers += Blocker.LOW_STORAGE
        if (!environment.nativeLibraryAvailable()) blockers += Blocker.UNSUPPORTED_ABI
        return Readiness(blockers.toList())
    }

    private fun locationBlockers(): List<Blocker> = buildList {
        val fine = environment.isGranted(FINE_LOCATION)
        val coarse = environment.isGranted(COARSE_LOCATION)
        when {
            !fine && !coarse -> add(Blocker.LOCATION_PERMISSION_MISSING)
            !fine -> add(Blocker.LOCATION_PRECISE_NOT_GRANTED)
        }
        when {
            !environment.hasLocationProvider() -> add(Blocker.NO_LOCATION_PROVIDER)
            !environment.locationEnabled() -> add(Blocker.LOCATION_SERVICES_OFF)
        }
    }

    companion object {
        const val CAMERA: String = "android.permission.CAMERA"
        const val FINE_LOCATION: String = "android.permission.ACCESS_FINE_LOCATION"
        const val COARSE_LOCATION: String = "android.permission.ACCESS_COARSE_LOCATION"
        val SDK_PERMISSIONS: Set<String> = setOf(CAMERA, FINE_LOCATION, COARSE_LOCATION)

        /** Free space needed before a capture: 50 MB (LLD 3.3 step 2). */
        const val MIN_FREE_BYTES: Long = 50L * 1024 * 1024
    }
}
