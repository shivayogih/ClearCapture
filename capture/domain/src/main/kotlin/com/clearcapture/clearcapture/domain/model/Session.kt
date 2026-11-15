package com.clearcapture.clearcapture.domain.model

/** Whether a session puts location into its photographs (SDK_REQUIREMENTS 7.1a). The host chooses. */
enum class LocationUse {
    /** Asked for; capture is blocked without a usable fix; GPS tags always written and verified. */
    REQUIRED,

    /** Asked for once; capture continues without it; GPS tags written when a fix exists. */
    OPTIONAL,

    /** Never asked for; no GPS tags. */
    OFF,
    ;

    /** [profile] with its location rule set for this session; gating and hints then follow automatically. */
    fun applyTo(profile: CaptureProfile): CaptureProfile {
        val required = this == REQUIRED
        val checks = if (required) profile.requiredChecks + QualityCheck.LOCATION else profile.requiredChecks - QualityCheck.LOCATION
        return profile.copy(location = profile.location.copy(required = required), requiredChecks = checks)
    }
}

/** Why a photograph has no location. */
enum class LocationNote { NONE, NOT_REQUESTED, PERMISSION_DENIED, LOCATION_OFF, NO_FIX }

/** One photograph the host asked for: its subject profile and an optional label such as "Bin front". */
data class ShotPlan(val profileId: String, val label: String?)

/**
 * What one capture session must produce (SDK_REQUIREMENTS 7.0). Pure data; validated where it is built.
 *
 * @property shots the planned photographs, in order; photos beyond the list use [defaultProfileId].
 * @property references the host's identifiers written into every photograph.
 */
data class SessionPlan(
    val shots: List<ShotPlan>,
    val defaultProfileId: String,
    val allowSubjectChange: Boolean,
    val minPhotos: Int,
    val maxPhotos: Int,
    /** The host's location mode, or `null` to let each subject profile decide. */
    val locationUse: LocationUse?,
    val references: Map<String, String>,
) {
    init {
        require(minPhotos in 1..maxPhotos) { "minPhotos must be between 1 and maxPhotos" }
    }

    /** The planned shot for the photo at [index] (0-based). */
    fun shotAt(index: Int): ShotPlan = shots.getOrNull(index) ?: ShotPlan(defaultProfileId, null)

    /** Every profile the session may need, for validation when it opens. */
    val profileIds: Set<String> get() = shots.map { it.profileId }.toSet() + defaultProfileId
}
