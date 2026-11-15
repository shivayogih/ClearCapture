package com.clearcapture.clearcapture.domain.usecase

import com.clearcapture.clearcapture.common.Clock
import com.clearcapture.clearcapture.domain.hint.HintPrioritizer
import com.clearcapture.clearcapture.domain.hint.HintStabilizer
import com.clearcapture.clearcapture.domain.hint.LiveGuidance
import com.clearcapture.clearcapture.domain.location.LocationFixPolicy
import com.clearcapture.clearcapture.domain.model.CaptureProfile
import com.clearcapture.clearcapture.domain.model.QualityReport
import com.clearcapture.clearcapture.domain.port.LocationSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/**
 * Turns live reports and location status into one stable [LiveGuidance] stream (LLD 3.2): prioritise, then
 * stabilise, sampled at most [updatesPerSecond] times a second so Compose is not flooded.
 */
class ObserveLiveQualityUseCase(
    private val reports: Flow<QualityReport?>,
    private val analyzerAvailable: Flow<Boolean?>,
    private val locationSource: LocationSource,
    private val clock: Clock,
    private val updatesPerSecond: Int = DEFAULT_UPDATES_PER_SECOND,
) {
    /** Guidance for [profile]. Cold; one stabiliser per collection. */
    operator fun invoke(profile: CaptureProfile): Flow<LiveGuidance> {
        val stabilizer = HintStabilizer(profile.hintHoldMillis)
        val ticks = flow {
            var n = 0L
            while (true) {
                emit(n++)
                delay(MILLIS_PER_SECOND / updatesPerSecond)
            }
        }
        return combine(ticks, reports, analyzerAvailable, locationSource.status) { _, report, available, location ->
            val candidate = HintPrioritizer.candidate(report, location, profile, analyzerAvailable = available != false)
            val hint = stabilizer.offer(candidate, clock.nowMillis())
            LiveGuidance(
                hint = hint,
                guideStatus = HintPrioritizer.guideStatus(hint, location),
                // Location is re-checked on every update so the shutter never waits for the hint hold to lock.
                captureEnabled = !hint.blocking && LocationFixPolicy.allowsCapture(location, profile.location),
                report = report,
                location = location,
            )
        }.distinctUntilChanged { old, new -> old.copy(report = null) == new.copy(report = null) }
    }

    private companion object {
        const val DEFAULT_UPDATES_PER_SECOND = 10
        const val MILLIS_PER_SECOND = 1_000L
    }
}
