package com.clearcapture.clearcapture.domain.location

import com.clearcapture.clearcapture.domain.model.CheckResult
import com.clearcapture.clearcapture.domain.model.LocationFix
import com.clearcapture.clearcapture.domain.model.LocationPolicy
import com.clearcapture.clearcapture.domain.model.LocationStatus
import com.clearcapture.clearcapture.domain.model.QualityCheck
import com.clearcapture.clearcapture.domain.model.ReasonCode
import com.clearcapture.clearcapture.domain.model.Verdict

/**
 * The location rules of a profile as pure functions (LLD section 8.1). Lives in the domain because
 * [com.clearcapture.clearcapture.domain.usecase] needs it to build the LOCATION check result.
 */
object LocationFixPolicy {

    /** Classifies [fix] at time [nowMillis]. An old fix is reported as stale before its accuracy is judged. */
    fun evaluate(fix: LocationFix, policy: LocationPolicy, nowMillis: Long): LocationStatus = when {
        nowMillis - fix.fixTimeEpochMillis > policy.maxAgeSeconds * MILLIS_PER_SECOND -> LocationStatus.Stale(fix)
        fix.accuracyMeters > policy.maxAccuracyMeters -> LocationStatus.Inaccurate(fix)
        else -> LocationStatus.Ready(fix)
    }

    /** True when [status] lets the shutter be pressed (LLD 3.3 step 1, 8.1). */
    fun allowsCapture(status: LocationStatus, policy: LocationPolicy): Boolean = when (status) {
        LocationStatus.NotRequired -> true
        is LocationStatus.Ready -> true
        is LocationStatus.Stale -> !policy.failOnStale
        is LocationStatus.Inaccurate -> !policy.failOnInaccurate
        LocationStatus.PermissionMissing, LocationStatus.ProviderDisabled, LocationStatus.Acquiring -> !policy.required
    }

    /** Why [status] blocks capture, or `null` when it does not. */
    fun blockReason(status: LocationStatus, policy: LocationPolicy): ReasonCode? = when {
        allowsCapture(status, policy) -> null
        status is LocationStatus.PermissionMissing -> ReasonCode.LOCATION_PERMISSION
        status is LocationStatus.ProviderDisabled -> ReasonCode.LOCATION_DISABLED
        status is LocationStatus.Stale -> ReasonCode.LOCATION_STALE
        status is LocationStatus.Inaccurate -> ReasonCode.LOCATION_INACCURATE
        else -> ReasonCode.LOCATION_NO_FIX
    }

    /**
     * The LOCATION check result for a saved photograph (LLD 3.3 steps 4 and 5).
     *
     * @param fix the fix passed to the camera at capture.
     * @param tagsPresent true when the GPS tags were found in the saved file (after writing any that were missing).
     */
    fun checkResult(fix: LocationFix?, policy: LocationPolicy, nowMillis: Long, tagsPresent: Boolean): CheckResult {
        val metrics = buildMap {
            put("tagsPresent", if (tagsPresent) 1.0 else 0.0)
            fix?.let {
                put("accuracyMeters", it.accuracyMeters.toDouble())
                put("ageSeconds", (nowMillis - it.fixTimeEpochMillis) / MILLIS_PER_SECOND.toDouble())
                put("isMock", if (it.isMock) 1.0 else 0.0)
            }
        }
        val thresholds = mapOf(
            "maxAgeSeconds" to policy.maxAgeSeconds.toDouble(),
            "maxAccuracyMeters" to policy.maxAccuracyMeters.toDouble(),
        )
        val (verdict, reason) = when {
            fix == null -> Verdict.FAIL to ReasonCode.LOCATION_NO_FIX
            !tagsPresent -> Verdict.FAIL to ReasonCode.LOCATION_TAGS_MISSING
            else -> when (evaluate(fix, policy, nowMillis)) {
                is LocationStatus.Stale -> (if (policy.failOnStale) Verdict.FAIL else Verdict.WARNING) to ReasonCode.LOCATION_STALE
                is LocationStatus.Inaccurate ->
                    (if (policy.failOnInaccurate) Verdict.FAIL else Verdict.WARNING) to ReasonCode.LOCATION_INACCURATE
                // A mock fix is flagged for review, never silently accepted as a pass.
                else -> if (fix.isMock) Verdict.WARNING to ReasonCode.LOCATION_MOCK else Verdict.PASS to null
            }
        }
        return CheckResult(QualityCheck.LOCATION, verdict, reason, metrics, thresholds)
    }

    private const val MILLIS_PER_SECOND = 1_000L
}
