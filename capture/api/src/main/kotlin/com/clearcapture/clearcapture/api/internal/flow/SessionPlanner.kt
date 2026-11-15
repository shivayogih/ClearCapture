package com.clearcapture.clearcapture.api.internal.flow

import com.clearcapture.clearcapture.api.CaptureConfig
import com.clearcapture.clearcapture.api.LocationMode
import com.clearcapture.clearcapture.domain.model.CaptureProfile
import com.clearcapture.clearcapture.domain.model.LocationUse
import com.clearcapture.clearcapture.domain.model.SessionPlan
import com.clearcapture.clearcapture.domain.model.ShotPlan

/** Turns the host's [CaptureConfig] into a session plan and its location needs. Pure; used before and during a session. */
internal class SessionPlanner(private val config: CaptureConfig) {

    /** The plan; the default subject falls back to `general`, then the first profile. */
    fun plan(profiles: List<CaptureProfile>): SessionPlan {
        val default = config.defaultProfileId ?: profiles.firstOrNull { it.id == GENERAL }?.id ?: profiles.firstOrNull()?.id ?: GENERAL
        return SessionPlan(
            shots = config.shots.map { ShotPlan(it.profileId, it.label) },
            defaultProfileId = default,
            allowSubjectChange = config.allowSubjectChange,
            minPhotos = config.minPhotos,
            maxPhotos = config.maxPhotos,
            locationUse = config.locationMode?.let { LocationUse.valueOf(it.name) },
            references = config.references,
        )
    }

    /** True when location must be granted and available before capture (never for OPTIONAL or OFF). */
    fun locationRequired(profiles: List<CaptureProfile>, plan: SessionPlan): Boolean = when (config.locationMode) {
        LocationMode.REQUIRED -> true
        LocationMode.OPTIONAL, LocationMode.OFF -> false
        null -> plan.profileIds.any { id -> profiles.firstOrNull { it.id == id }?.location?.required ?: false }
    }

    private companion object {
        const val GENERAL = "general"
    }
}
