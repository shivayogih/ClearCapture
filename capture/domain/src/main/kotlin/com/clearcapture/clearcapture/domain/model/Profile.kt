package com.clearcapture.clearcapture.domain.model

/** What is being photographed, with its guide, required checks, thresholds and acceptance rules. */
data class CaptureProfile(
    val id: String,
    /** String resource name of the title, for example `cc_profile_container`. */
    val titleKey: String,
    /** `null` for free capture. */
    val guide: GuideRegion?,
    val guideShape: GuideShape,
    /** Contains [QualityCheck.LOCATION] exactly when [location] is required. */
    val requiredChecks: Set<QualityCheck>,
    /** A FAIL from one of these checks is downgraded to WARNING. */
    val warningOnlyChecks: Set<QualityCheck>,
    val thresholds: Thresholds,
    /** `location.required` is the single source of truth for whether location is needed. */
    val location: LocationPolicy,
    val allowAcceptOnWarning: Boolean,
    val allowOverrideOnFail: Boolean,
    val hintHoldMillis: Long,
    /** Applied by [ProfileResolver] once the camera reports its traits. */
    val deviceOverrides: List<DeviceOverride> = emptyList(),
    val schemaVersion: Int = 1,
)

/** Blur limits for one stage (Laplacian variance of the guide region). */
data class BlurLimits(val fail: Double, val warn: Double)

/** Every tunable threshold of the six validators. Mirrors the `thresholds` object of the profile JSON. */
data class Thresholds(
    val liveBlur: BlurLimits,
    val finalBlur: BlurLimits,
    val lumaMin: Double,
    val lumaMax: Double,
    val darkClipMax: Double,
    val brightClipMax: Double,
    val glareWarnPct: Double,
    val glareFailPct: Double,
    val glareBlobWarnPct: Double,
    val glareBlobFailPct: Double,
    val coverageMin: Double,
    val coverageMax: Double,
    val minLongSide: Int,
    val minShortSide: Int,
    val minFileBytes: Long,
    val cellEdgeMin: Double,
    val cellStdMin: Double,
    val obstructWarnPct: Double,
    val obstructFailPct: Double,
) {
    /** Blur limits for [stage]. */
    fun blur(stage: Stage): BlurLimits = if (stage == Stage.LIVE) liveBlur else finalBlur

    /** Returns these thresholds with every non-null value of [patch] applied. */
    // One elvis per field is a flat mapping, not branching logic.
    @Suppress("CyclomaticComplexMethod")
    fun apply(patch: ThresholdsPatch): Thresholds = copy(
        liveBlur = BlurLimits(patch.liveBlurFail ?: liveBlur.fail, patch.liveBlurWarn ?: liveBlur.warn),
        finalBlur = BlurLimits(patch.finalBlurFail ?: finalBlur.fail, patch.finalBlurWarn ?: finalBlur.warn),
        lumaMin = patch.lumaMin ?: lumaMin,
        lumaMax = patch.lumaMax ?: lumaMax,
        darkClipMax = patch.darkClipMax ?: darkClipMax,
        brightClipMax = patch.brightClipMax ?: brightClipMax,
        glareWarnPct = patch.glareWarnPct ?: glareWarnPct,
        glareFailPct = patch.glareFailPct ?: glareFailPct,
        glareBlobWarnPct = patch.glareBlobWarnPct ?: glareBlobWarnPct,
        glareBlobFailPct = patch.glareBlobFailPct ?: glareBlobFailPct,
        coverageMin = patch.coverageMin ?: coverageMin,
        coverageMax = patch.coverageMax ?: coverageMax,
        minLongSide = patch.minLongSide ?: minLongSide,
        minShortSide = patch.minShortSide ?: minShortSide,
        minFileBytes = patch.minFileBytes ?: minFileBytes,
        cellEdgeMin = patch.cellEdgeMin ?: cellEdgeMin,
        cellStdMin = patch.cellStdMin ?: cellStdMin,
        obstructWarnPct = patch.obstructWarnPct ?: obstructWarnPct,
        obstructFailPct = patch.obstructFailPct ?: obstructFailPct,
    )
}

/** A partial set of thresholds; `null` means "keep the inherited value". */
data class ThresholdsPatch(
    val liveBlurFail: Double? = null,
    val liveBlurWarn: Double? = null,
    val finalBlurFail: Double? = null,
    val finalBlurWarn: Double? = null,
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

/** Location rules of a profile. */
data class LocationPolicy(
    val required: Boolean,
    val maxAgeSeconds: Int,
    val maxAccuracyMeters: Float,
    val failOnStale: Boolean,
    val failOnInaccurate: Boolean,
)

/** What the camera reported at bind time, without Android types. */
data class CameraTraits(val fixedFocus: Boolean, val hasFlash: Boolean, val hardwareLevel: String)

/** Which cameras an override applies to. `null` fields match anything. Never matches on model name. */
data class CameraTraitsMatch(
    val fixedFocus: Boolean? = null,
    val hasFlash: Boolean? = null,
    val hardwareLevel: String? = null,
) {
    /** True when every non-null field equals the camera's trait. */
    fun matches(traits: CameraTraits): Boolean =
        (fixedFocus == null || fixedFocus == traits.fixedFocus) &&
            (hasFlash == null || hasFlash == traits.hasFlash) &&
            (hardwareLevel == null || hardwareLevel == traits.hardwareLevel)
}

/** Threshold changes for cameras whose traits match. */
data class DeviceOverride(val match: CameraTraitsMatch, val thresholds: ThresholdsPatch)

/** Applies device overrides to a profile once the camera's traits are known. Pure. */
object ProfileResolver {
    /** Returns [profile] with every override that matches [cameraTraits] applied, in list order. */
    fun resolve(profile: CaptureProfile, cameraTraits: CameraTraits): CaptureProfile =
        profile.deviceOverrides
            .filter { it.match.matches(cameraTraits) }
            .fold(profile) { acc, override -> acc.copy(thresholds = acc.thresholds.apply(override.thresholds)) }
}
