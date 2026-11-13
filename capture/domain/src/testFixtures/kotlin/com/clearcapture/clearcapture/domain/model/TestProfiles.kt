package com.clearcapture.clearcapture.domain.model

/** Profiles with the default thresholds of LLD section 6.2, for tests in any module. */
object TestProfiles {

    /** Default thresholds from the bundled profile JSON. */
    val defaultThresholds: Thresholds = Thresholds(
        liveBlur = BlurLimits(fail = 60.0, warn = 100.0),
        finalBlur = BlurLimits(fail = 120.0, warn = 180.0),
        lumaMin = 60.0,
        lumaMax = 200.0,
        darkClipMax = 0.35,
        brightClipMax = 0.15,
        glareWarnPct = 0.03,
        glareFailPct = 0.08,
        glareBlobWarnPct = 0.01,
        glareBlobFailPct = 0.03,
        coverageMin = 0.45,
        coverageMax = 0.98,
        minLongSide = 1920,
        minShortSide = 1080,
        minFileBytes = 200_000,
        cellEdgeMin = 0.015,
        cellStdMin = 8.0,
        obstructWarnPct = 0.20,
        obstructFailPct = 0.35,
    )

    /** Default location policy: required, 60 s, 50 m. */
    val defaultLocation: LocationPolicy = LocationPolicy(
        required = true,
        maxAgeSeconds = 60,
        maxAccuracyMeters = 50f,
        failOnStale = false,
        failOnInaccurate = false,
    )

    /** The container profile. */
    fun container(): CaptureProfile = CaptureProfile(
        id = "container",
        titleKey = "cc_profile_container",
        guide = GuideRegion(0.18f, 0.16f, 0.82f, 0.92f),
        guideShape = GuideShape.CONTAINER,
        requiredChecks = QualityCheck.entries.toSet(),
        warningOnlyChecks = setOf(QualityCheck.FRAMING, QualityCheck.OBSTRUCTION),
        thresholds = defaultThresholds,
        location = defaultLocation,
        allowAcceptOnWarning = true,
        allowOverrideOnFail = false,
        hintHoldMillis = 400,
    )

    /** The general profile: no guide. */
    fun general(): CaptureProfile = container().copy(
        id = "general",
        titleKey = "cc_profile_general",
        guide = null,
        guideShape = GuideShape.NONE,
        requiredChecks = setOf(
            QualityCheck.BLUR, QualityCheck.EXPOSURE, QualityCheck.RESOLUTION,
            QualityCheck.OBSTRUCTION, QualityCheck.LOCATION,
        ),
        warningOnlyChecks = setOf(QualityCheck.OBSTRUCTION),
    )
}
