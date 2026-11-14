package com.clearcapture.clearcapture.domain.session

import com.clearcapture.clearcapture.common.Clock
import com.clearcapture.clearcapture.domain.model.CaptureProfile
import com.clearcapture.clearcapture.domain.model.FrameData
import com.clearcapture.clearcapture.domain.model.QualityReport
import com.clearcapture.clearcapture.domain.port.FrameConsumer
import com.clearcapture.clearcapture.domain.port.QualityAnalyzer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Timing of the latest analysed frames, for the debug overlay and the adaptive step-down. */
data class LiveTiming(val lastMillis: Long, val framesPerSecond: Double, val overBudgetStreak: Int)

/**
 * Receives live frames on the camera's analysis thread, analyses them synchronously with the active profile
 * and publishes the latest report. The frame is never retained.
 */
class LiveAnalysisPipeline(
    private val analyzer: QualityAnalyzer,
    private val clock: Clock,
    private val budgetMillis: Long = DEFAULT_BUDGET_MILLIS,
) : FrameConsumer {

    @Volatile
    private var profile: CaptureProfile? = null

    private val mutableReports = MutableStateFlow<QualityReport?>(null)

    /** The latest live report. */
    val reports: StateFlow<QualityReport?> = mutableReports.asStateFlow()

    private val mutableTiming = MutableStateFlow(LiveTiming(0, 0.0, 0))

    /** Timing of the latest frames. */
    val timing: StateFlow<LiveTiming> = mutableTiming.asStateFlow()

    private var lastFrameNanos = 0L

    /** Sets the profile frames are analysed against; `null` pauses analysis. */
    fun setProfile(profile: CaptureProfile?) {
        this.profile = profile
        if (profile == null) mutableReports.value = null
    }

    override fun onFrame(frame: FrameData) {
        val active = profile ?: return
        val start = clock.elapsedNanos()
        val report = analyzer.analyzeLive(frame, active)
        val end = clock.elapsedNanos()
        val millis = (end - start) / NANOS_PER_MILLI
        val previous = mutableTiming.value
        val fps = if (lastFrameNanos == 0L) 0.0 else NANOS_PER_SECOND / (start - lastFrameNanos).coerceAtLeast(1)
        lastFrameNanos = start
        mutableTiming.value = LiveTiming(
            lastMillis = millis,
            framesPerSecond = fps,
            overBudgetStreak = if (millis > budgetMillis) previous.overBudgetStreak + 1 else 0,
        )
        mutableReports.value = report
    }

    private companion object {
        const val DEFAULT_BUDGET_MILLIS = 60L
        const val NANOS_PER_MILLI = 1_000_000L
        const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}
