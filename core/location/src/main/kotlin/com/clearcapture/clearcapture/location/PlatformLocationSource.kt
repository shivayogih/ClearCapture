package com.clearcapture.clearcapture.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import com.clearcapture.clearcapture.common.Clock
import com.clearcapture.clearcapture.common.Logger

/**
 * Location straight from the GPS provider, every second with no distance filter. Works without Play Services
 * and without mobile data.
 */
class PlatformLocationSource(context: Context, clock: Clock, logger: Logger) : BaseLocationSource(context, clock, logger) {

    private val manager = context.getSystemService(LocationManager::class.java)
    private val listener = LocationListener(::onLocation)

    @SuppressLint("MissingPermission") // Checked by the base class before this is called.
    override fun startUpdates() {
        val lm = manager ?: return
        lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let(::onLocation)
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, INTERVAL_MILLIS, 0f, listener, Looper.getMainLooper())
    }

    override fun stopUpdates() {
        manager?.removeUpdates(listener)
    }

    private companion object {
        const val INTERVAL_MILLIS = 1_000L
    }
}
