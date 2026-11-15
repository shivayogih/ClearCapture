package com.clearcapture.clearcapture.data.profile

import kotlinx.serialization.Serializable

/** Root of the profile JSON (LLD section 6.2). Every field a profile may omit is nullable. */
@Serializable
internal data class ProfileFileDto(
    val schemaVersion: Int,
    val defaults: DefaultsDto,
    val profiles: List<ProfileDto>,
    val deviceOverrides: List<DeviceOverrideDto> = emptyList(),
)

@Serializable
internal data class DefaultsDto(
    val hintHoldMillis: Long,
    val allowAcceptOnWarning: Boolean,
    val allowOverrideOnFail: Boolean,
    val location: LocationDto,
    val thresholds: ThresholdsDto,
)

@Serializable
internal data class ProfileDto(
    val id: String,
    val titleKey: String,
    val guideShape: String,
    val guide: GuideDto? = null,
    val requiredChecks: List<String>,
    val warningOnlyChecks: List<String> = emptyList(),
    val thresholds: ThresholdsDto? = null,
    val location: LocationDto? = null,
    val hintHoldMillis: Long? = null,
    val allowAcceptOnWarning: Boolean? = null,
    val allowOverrideOnFail: Boolean? = null,
)

@Serializable
internal data class GuideDto(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** Location rules. In a profile, absent fields inherit from the defaults. */
@Serializable
internal data class LocationDto(
    val required: Boolean? = null,
    val maxAgeSeconds: Int? = null,
    val maxAccuracyMeters: Float? = null,
    val failOnStale: Boolean? = null,
    val failOnInaccurate: Boolean? = null,
)

@Serializable
internal data class BlurDto(val blurFail: Double? = null, val blurWarn: Double? = null)

/** Thresholds. In the defaults every field is required; elsewhere absent fields inherit. */
@Serializable
internal data class ThresholdsDto(
    val live: BlurDto? = null,
    val final: BlurDto? = null,
    val lumaMin: Double? = null,
    val lumaMax: Double? = null,
    val darkClipMax: Double? = null,
    val brightClipMax: Double? = null,
    val glareWarnPct: Double? = null,
    val glareFailPct: Double? = null,
    val glareBlobWarnPct: Double? = null,
    val glareBlobFailPct: Double? = null,
    val coverageMin: Double? = null,
    val coverageMax: Double? = null,
    val minLongSide: Int? = null,
    val minShortSide: Int? = null,
    val minFileBytes: Long? = null,
    val cellEdgeMin: Double? = null,
    val cellStdMin: Double? = null,
    val obstructWarnPct: Double? = null,
    val obstructFailPct: Double? = null,
)

@Serializable
internal data class DeviceOverrideDto(val match: MatchDto, val thresholds: ThresholdsDto)

@Serializable
internal data class MatchDto(
    val fixedFocus: Boolean? = null,
    val hasFlash: Boolean? = null,
    val hardwareLevel: String? = null,
)
