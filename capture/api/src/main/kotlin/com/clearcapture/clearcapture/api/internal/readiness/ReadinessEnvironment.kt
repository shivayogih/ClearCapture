package com.clearcapture.clearcapture.api.internal.readiness

/** What readiness needs to know about the device and the app. Read-only; implementations change nothing. */
internal interface ReadinessEnvironment {
    /** Permissions the merged manifest declares. */
    fun declaredPermissions(): Set<String>

    /** True when [permission] is granted. */
    fun isGranted(permission: String): Boolean

    /** True when the device has a back-facing camera. */
    fun hasBackCamera(): Boolean

    /** True when a device policy disables the camera. */
    fun cameraDisabledByPolicy(): Boolean

    /** True when the device has a GPS or network location provider. */
    fun hasLocationProvider(): Boolean

    /** True when location services are switched on. */
    fun locationEnabled(): Boolean

    /** Free bytes where pending photographs are written. */
    fun freeBytes(): Long

    /** True when the SDK's native library is packaged for one of this device's ABIs. */
    fun nativeLibraryAvailable(): Boolean
}
