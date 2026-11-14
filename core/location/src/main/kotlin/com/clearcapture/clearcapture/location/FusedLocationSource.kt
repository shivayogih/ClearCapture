package com.clearcapture.clearcapture.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.clearcapture.clearcapture.common.Clock
import com.clearcapture.clearcapture.common.Logger
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/** Location from the fused provider, high accuracy, every second. Used when Play Services is available. */
class FusedLocationSource(context: Context, clock: Clock, logger: Logger) : BaseLocationSource(context, clock, logger) {

    private val client = LocationServices.getFusedLocationProviderClient(context)
    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let(::onLocation)
        }
    }

    @SuppressLint("MissingPermission") // Checked by the base class before this is called.
    override fun startUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MILLIS)
            .setMinUpdateIntervalMillis(INTERVAL_MILLIS)
            .build()
        client.lastLocation.addOnSuccessListener { last -> last?.let(::onLocation) }
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    override fun stopUpdates() {
        client.removeLocationUpdates(callback)
    }

    private companion object {
        const val INTERVAL_MILLIS = 1_000L
    }
}
