package com.clearcapture.clearcapture.data.profile

import com.clearcapture.clearcapture.common.Outcome
import com.clearcapture.clearcapture.domain.model.BlurLimits
import com.clearcapture.clearcapture.domain.model.CameraTraitsMatch
import com.clearcapture.clearcapture.domain.model.CaptureProfile
import com.clearcapture.clearcapture.domain.model.DeviceOverride
import com.clearcapture.clearcapture.domain.model.GuideRegion
import com.clearcapture.clearcapture.domain.model.GuideShape
import com.clearcapture.clearcapture.domain.model.LocationPolicy
import com.clearcapture.clearcapture.domain.model.QualityCheck
import com.clearcapture.clearcapture.domain.model.Thresholds
import com.clearcapture.clearcapture.domain.model.ThresholdsPatch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Turns profile JSON into validated domain profiles. Pure: no Android, no I/O.
 *
 * A profile inherits the defaults and overrides only what it lists. `location.required` decides
 * whether [QualityCheck.LOCATION] is in `requiredChecks`, whatever the file says.
 */
internal object ProfileParser {

    /** Schema versions this SDK reads: the current one and the previous one. */
    val SUPPORTED_SCHEMA_VERSIONS: Set<Int> = setOf(1)

    private val json = Json { ignoreUnknownKeys = true }

    /** Parses and validates [text]. Never throws. */
    fun parse(text: String): Outcome<List<CaptureProfile>, String> {
        val file = try {
            json.decodeFromString(ProfileFileDto.serializer(), text)
        } catch (e: SerializationException) {
            return Outcome.Failure("Profile JSON is malformed: ${e.message}")
        } catch (e: IllegalArgumentException) {
            return Outcome.Failure("Profile JSON is malformed: ${e.message}")
        }
        return try {
            Outcome.Success(map(file))
        } catch (e: ProfileValidationException) {
            Outcome.Failure(e.message.orEmpty())
        }
    }

    private fun map(file: ProfileFileDto): List<CaptureProfile> {
        check(file.schemaVersion in SUPPORTED_SCHEMA_VERSIONS) { "Unsupported schemaVersion ${file.schemaVersion}" }
        check(file.profiles.isNotEmpty()) { "No profiles" }
        val ids = file.profiles.map { it.id }
        check(ids.none { it.isBlank() }) { "A profile has a blank id" }
        check(ids.toSet().size == ids.size) { "Duplicate profile ids: $ids" }

        val defaultThresholds = completeThresholds(file.defaults.thresholds)
        val defaultLocation = completeLocation(file.defaults.location)
        val overrides = file.deviceOverrides.map { it.toDomain() }

        return file.profiles.map { dto ->
            val thresholds = dto.thresholds?.let { defaultThresholds.apply(it.toPatch()) } ?: defaultThresholds
            validate(dto.id, thresholds)
            dto.toDomain(file.defaults, defaultLocation, thresholds, overrides, file.schemaVersion)
        }
    }

    private fun ProfileDto.toDomain(
        defaults: DefaultsDto,
        defaultLocation: LocationPolicy,
        thresholds: Thresholds,
        overrides: List<DeviceOverride>,
        schemaVersion: Int,
    ): CaptureProfile {
        val shape = enumValue<GuideShape>(guideShape, "guideShape of $id")
        val region = guide?.let {
            try {
                GuideRegion(it.left, it.top, it.right, it.bottom)
            } catch (e: IllegalArgumentException) {
                throw ProfileValidationException("Profile $id: ${e.message}", e)
            }
        }
        check((shape == GuideShape.NONE) == (region == null)) { "Profile $id: a guide is needed exactly when guideShape is not NONE" }
        val locationPolicy = location?.let { defaultLocation.merge(it) } ?: defaultLocation
        val listed = requiredChecks.map { enumValue<QualityCheck>(it, "requiredChecks of $id") }.toSet()
        val required = if (locationPolicy.required) listed + QualityCheck.LOCATION else listed - QualityCheck.LOCATION
        val warningOnly = warningOnlyChecks.map { enumValue<QualityCheck>(it, "warningOnlyChecks of $id") }.toSet()
        val hold = hintHoldMillis ?: defaults.hintHoldMillis
        check(hold in 0..MAX_HINT_HOLD_MILLIS) { "Profile $id: hintHoldMillis out of range" }
        return CaptureProfile(
            id = id,
            titleKey = titleKey,
            guide = region,
            guideShape = shape,
            requiredChecks = required,
            warningOnlyChecks = warningOnly,
            thresholds = thresholds,
            location = locationPolicy,
            allowAcceptOnWarning = allowAcceptOnWarning ?: defaults.allowAcceptOnWarning,
            allowOverrideOnFail = allowOverrideOnFail ?: defaults.allowOverrideOnFail,
            hintHoldMillis = hold,
            deviceOverrides = overrides,
            schemaVersion = schemaVersion,
        )
    }

    private fun completeThresholds(dto: ThresholdsDto): Thresholds {
        fun <T : Any> req(value: T?, name: String): T =
            value ?: throw ProfileValidationException("defaults.thresholds.$name is missing")
        val thresholds = Thresholds(
            liveBlur = BlurLimits(req(dto.live?.blurFail, "live.blurFail"), req(dto.live?.blurWarn, "live.blurWarn")),
            finalBlur = BlurLimits(req(dto.final?.blurFail, "final.blurFail"), req(dto.final?.blurWarn, "final.blurWarn")),
            lumaMin = req(dto.lumaMin, "lumaMin"),
            lumaMax = req(dto.lumaMax, "lumaMax"),
            darkClipMax = req(dto.darkClipMax, "darkClipMax"),
            brightClipMax = req(dto.brightClipMax, "brightClipMax"),
            glareWarnPct = req(dto.glareWarnPct, "glareWarnPct"),
            glareFailPct = req(dto.glareFailPct, "glareFailPct"),
            glareBlobWarnPct = req(dto.glareBlobWarnPct, "glareBlobWarnPct"),
            glareBlobFailPct = req(dto.glareBlobFailPct, "glareBlobFailPct"),
            coverageMin = req(dto.coverageMin, "coverageMin"),
            coverageMax = req(dto.coverageMax, "coverageMax"),
            minLongSide = req(dto.minLongSide, "minLongSide"),
            minShortSide = req(dto.minShortSide, "minShortSide"),
            minFileBytes = req(dto.minFileBytes, "minFileBytes"),
            cellEdgeMin = req(dto.cellEdgeMin, "cellEdgeMin"),
            cellStdMin = req(dto.cellStdMin, "cellStdMin"),
            obstructWarnPct = req(dto.obstructWarnPct, "obstructWarnPct"),
            obstructFailPct = req(dto.obstructFailPct, "obstructFailPct"),
        )
        validate("defaults", thresholds)
        return thresholds
    }

    private fun completeLocation(dto: LocationDto): LocationPolicy {
        fun <T : Any> req(value: T?, name: String): T =
            value ?: throw ProfileValidationException("defaults.location.$name is missing")
        val policy = LocationPolicy(
            required = req(dto.required, "required"),
            maxAgeSeconds = req(dto.maxAgeSeconds, "maxAgeSeconds"),
            maxAccuracyMeters = req(dto.maxAccuracyMeters, "maxAccuracyMeters"),
            failOnStale = dto.failOnStale ?: false,
            failOnInaccurate = dto.failOnInaccurate ?: false,
        )
        validate("defaults", policy)
        return policy
    }

    private fun LocationPolicy.merge(dto: LocationDto): LocationPolicy = copy(
        required = dto.required ?: required,
        maxAgeSeconds = dto.maxAgeSeconds ?: maxAgeSeconds,
        maxAccuracyMeters = dto.maxAccuracyMeters ?: maxAccuracyMeters,
        failOnStale = dto.failOnStale ?: failOnStale,
        failOnInaccurate = dto.failOnInaccurate ?: failOnInaccurate,
    ).also { validate("profile", it) }

    private fun validate(owner: String, policy: LocationPolicy) {
        check(policy.maxAgeSeconds > 0) { "$owner: location.maxAgeSeconds must be positive" }
        check(policy.maxAccuracyMeters > 0f) { "$owner: location.maxAccuracyMeters must be positive" }
    }

    private fun validate(owner: String, t: Thresholds) {
        check(t.liveBlur.fail <= t.liveBlur.warn) { "$owner: live blurFail must not exceed blurWarn" }
        check(t.finalBlur.fail <= t.finalBlur.warn) { "$owner: final blurFail must not exceed blurWarn" }
        check(t.lumaMin in 0.0..LUMA_MAX && t.lumaMax in 0.0..LUMA_MAX && t.lumaMin < t.lumaMax) { "$owner: luma limits invalid" }
        val shares = listOf(
            t.darkClipMax, t.brightClipMax, t.glareWarnPct, t.glareFailPct, t.glareBlobWarnPct, t.glareBlobFailPct,
            t.coverageMin, t.coverageMax, t.cellEdgeMin, t.obstructWarnPct, t.obstructFailPct,
        )
        check(shares.all { it in 0.0..1.0 }) { "$owner: shares must be within 0..1" }
        check(t.glareWarnPct <= t.glareFailPct && t.glareBlobWarnPct <= t.glareBlobFailPct) { "$owner: glare warn above fail" }
        check(t.obstructWarnPct <= t.obstructFailPct) { "$owner: obstruction warn above fail" }
        check(t.coverageMin < t.coverageMax) { "$owner: coverageMin must be below coverageMax" }
        check(t.minLongSide >= t.minShortSide && t.minShortSide > 0) { "$owner: resolution limits invalid" }
        check(t.minFileBytes >= 0 && t.cellStdMin >= 0) { "$owner: negative limit" }
    }

    private fun DeviceOverrideDto.toDomain() = DeviceOverride(
        match = CameraTraitsMatch(match.fixedFocus, match.hasFlash, match.hardwareLevel),
        thresholds = thresholds.toPatch(),
    )

    private fun ThresholdsDto.toPatch() = ThresholdsPatch(
        liveBlurFail = live?.blurFail,
        liveBlurWarn = live?.blurWarn,
        finalBlurFail = final?.blurFail,
        finalBlurWarn = final?.blurWarn,
        lumaMin = lumaMin,
        lumaMax = lumaMax,
        darkClipMax = darkClipMax,
        brightClipMax = brightClipMax,
        glareWarnPct = glareWarnPct,
        glareFailPct = glareFailPct,
        glareBlobWarnPct = glareBlobWarnPct,
        glareBlobFailPct = glareBlobFailPct,
        coverageMin = coverageMin,
        coverageMax = coverageMax,
        minLongSide = minLongSide,
        minShortSide = minShortSide,
        minFileBytes = minFileBytes,
        cellEdgeMin = cellEdgeMin,
        cellStdMin = cellStdMin,
        obstructWarnPct = obstructWarnPct,
        obstructFailPct = obstructFailPct,
    )

    private inline fun <reified E : Enum<E>> enumValue(value: String, field: String): E =
        enumValues<E>().firstOrNull { it.name == value } ?: throw ProfileValidationException("Unknown value '$value' in $field")

    private inline fun check(condition: Boolean, message: () -> String) {
        if (!condition) throw ProfileValidationException(message())
    }

    private const val LUMA_MAX = 255.0
    private const val MAX_HINT_HOLD_MILLIS = 5_000L
}

/** Thrown inside [ProfileParser] only; never leaves it. */
private class ProfileValidationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
