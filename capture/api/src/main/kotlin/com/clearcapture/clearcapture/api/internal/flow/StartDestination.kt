package com.clearcapture.clearcapture.api.internal.flow

import com.clearcapture.clearcapture.api.Blocker
import com.clearcapture.clearcapture.api.Readiness
import com.clearcapture.clearcapture.ui.navigation.CaptureDestination

/** Where an SDK-managed flow starts: the permission step only when something it can fix is missing. Pure. */
internal object StartDestination {
    private val resolvable = setOf(
        Blocker.CAMERA_PERMISSION_MISSING,
        Blocker.LOCATION_PERMISSION_MISSING,
        Blocker.LOCATION_PRECISE_NOT_GRANTED,
        Blocker.LOCATION_SERVICES_OFF,
    )

    fun of(readiness: Readiness, profileId: String?): CaptureDestination = when {
        readiness.blockers.any { it in resolvable } -> CaptureDestination.Permission(profileId)
        profileId != null -> CaptureDestination.Camera(profileId)
        else -> CaptureDestination.Profile
    }
}
