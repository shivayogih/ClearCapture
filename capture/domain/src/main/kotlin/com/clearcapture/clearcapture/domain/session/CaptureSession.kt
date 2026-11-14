package com.clearcapture.clearcapture.domain.session

import com.clearcapture.clearcapture.common.Outcome
import com.clearcapture.clearcapture.domain.model.LocationPolicy
import com.clearcapture.clearcapture.domain.port.LocationSource
import com.clearcapture.clearcapture.domain.port.QualityAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One run of the capture flow (LLD 3.2). Started as soon as permissions are granted, whichever screen is shown,
 * so location and OpenCV are warming up before the camera opens. Closed when the flow ends.
 */
class CaptureSession(
    private val locationSource: LocationSource,
    private val analyzer: QualityAnalyzer,
    private val scope: CoroutineScope,
) {
    private val mutableAnalyzerAvailable = MutableStateFlow<Boolean?>(null)

    /** `null` while OpenCV is loading, then whether it loaded. */
    val analyzerAvailable: StateFlow<Boolean?> = mutableAnalyzerAvailable.asStateFlow()

    private var warmUp: Job? = null
    private var started = false

    /** Starts location updates under [policy] and warms up the analyzer. Safe to call again with a new policy. */
    fun start(policy: LocationPolicy) {
        locationSource.start(policy)
        if (started) return
        started = true
        warmUp = scope.launch { mutableAnalyzerAvailable.value = analyzer.warmUp() is Outcome.Success }
    }

    /** Stops location updates. */
    fun close() {
        locationSource.stop()
        warmUp?.cancel()
        started = false
    }
}
