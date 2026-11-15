package com.clearcapture.clearcapture.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.clearcapture.clearcapture.common.Clock
import com.clearcapture.clearcapture.common.Logger
import com.clearcapture.clearcapture.domain.location.LocationFixPolicy
import com.clearcapture.clearcapture.domain.model.LocationFix
import com.clearcapture.clearcapture.domain.model.LocationPolicy
import com.clearcapture.clearcapture.domain.model.LocationStatus
import com.clearcapture.clearcapture.domain.port.LocationSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Shared behaviour of the location sources: status as a state flow, re-evaluated every second so a fix
 * turns stale on time and permission or location-services changes are noticed without a callback.
 */
abstract class BaseLocationSource(
    protected val context: Context,
    private val clock: Clock,
    protected val logger: Logger,
) : LocationSource {

    private val mutableStatus = MutableStateFlow<LocationStatus>(LocationStatus.Acquiring)
    override val status: StateFlow<LocationStatus> = mutableStatus.asStateFlow()

    private var scope: CoroutineScope? = null
    private var ticker: Job? = null
    private var policy: LocationPolicy? = null
    private var lastFix: LocationFix? = null
    private var updatesRunning = false

    override fun start(policy: LocationPolicy) {
        stop()
        this.policy = policy
        if (!policy.required) {
            mutableStatus.value = LocationStatus.NotRequired
            return
        }
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = newScope
        ticker = newScope.launch {
            while (isActive) {
                refresh()
                delay(TICK_MILLIS)
            }
        }
    }

    override fun stop() {
        scope?.cancel()
        scope = null
        ticker = null
        if (updatesRunning) {
            stopUpdates()
            updatesRunning = false
        }
    }

    override fun current(): LocationStatus = mutableStatus.value

    /** Starts platform updates; called on the main thread once permission and location services are available. */
    protected abstract fun startUpdates()

    /** Stops platform updates. */
    protected abstract fun stopUpdates()

    /** Implementations call this from their location callback. */
    protected fun onLocation(location: Location) {
        lastFix = location.toFix()
        refresh()
    }

    private fun refresh() {
        val active = policy ?: return
        mutableStatus.value = when {
            !hasPermission() -> LocationStatus.PermissionMissing.also { stopIfRunning() }
            !locationEnabled() -> LocationStatus.ProviderDisabled.also { stopIfRunning() }
            else -> {
                startIfStopped()
                lastFix?.let { LocationFixPolicy.evaluate(it, active, clock.nowMillis()) } ?: LocationStatus.Acquiring
            }
        }
    }

    private fun startIfStopped() {
        if (updatesRunning) return
        try {
            startUpdates()
            updatesRunning = true
        } catch (e: SecurityException) {
            logger.warn(TAG, "Location updates refused", e)
        }
    }

    private fun stopIfRunning() {
        if (!updatesRunning) return
        stopUpdates()
        updatesRunning = false
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun locationEnabled(): Boolean =
        context.getSystemService(LocationManager::class.java)?.let(LocationManagerCompat::isLocationEnabled) ?: false

    private fun Location.toFix(): LocationFix = LocationFix(
        latitude = latitude,
        longitude = longitude,
        altitudeMeters = if (hasAltitude()) altitude else null,
        accuracyMeters = if (hasAccuracy()) accuracy else Float.MAX_VALUE,
        fixTimeEpochMillis = time,
        provider = provider ?: "unknown",
        isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) isMock else @Suppress("DEPRECATION") isFromMockProvider,
    )

    private companion object {
        const val TAG = "CcLocation"
        const val TICK_MILLIS = 1_000L
    }
}
