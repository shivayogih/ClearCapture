package com.clearcapture.clearcapture.api

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

/**
 * What the host asks for when it launches the capture flow.
 *
 * @property profileId the profile to use; `null` shows the profile screen.
 * @property requestId an identifier the host can use to match the result to its request.
 * @property outputDirectory where accepted photographs are stored; `null` uses the SDK default.
 *   It must be inside the app's private storage (`filesDir`, `cacheDir` or the app's external files directory).
 * @property allowAcceptOnWarning overrides the profile's rule for this launch; `null` keeps the profile's value.
 * @property allowOverrideOnFail overrides the profile's rule for this launch; `null` keeps the profile's value.
 */
@Parcelize
public data class CaptureConfig @JvmOverloads constructor(
    val profileId: String? = null,
    val requestId: String = UUID.randomUUID().toString(),
    val outputDirectory: String? = null,
    val allowAcceptOnWarning: Boolean? = null,
    val allowOverrideOnFail: Boolean? = null,
) : Parcelable
