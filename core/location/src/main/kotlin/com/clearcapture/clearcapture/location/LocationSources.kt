package com.clearcapture.clearcapture.location

import android.content.Context
import com.clearcapture.clearcapture.common.Clock
import com.clearcapture.clearcapture.common.Logger
import com.clearcapture.clearcapture.domain.port.LocationSource
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

/** Chooses the location source once per flow: fused when Play Services is available, platform GPS otherwise. */
object LocationSources {
    /** Creates the best available source. */
    fun create(context: Context, clock: Clock, logger: Logger): LocationSource {
        val playServices = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        return if (playServices) FusedLocationSource(context, clock, logger) else PlatformLocationSource(context, clock, logger)
    }
}
