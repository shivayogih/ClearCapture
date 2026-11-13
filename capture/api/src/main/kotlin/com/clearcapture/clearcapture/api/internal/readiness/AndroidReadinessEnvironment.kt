package com.clearcapture.clearcapture.api.internal.readiness

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/** Reads readiness facts from the Android framework. Every method is a read; nothing is changed. */
internal class AndroidReadinessEnvironment(private val context: Context) : ReadinessEnvironment {

    override fun declaredPermissions(): Set<String> = try {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        info.requestedPermissions.orEmpty().toSet()
    } catch (_: PackageManager.NameNotFoundException) {
        emptySet()
    }

    override fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    override fun hasBackCamera(): Boolean {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) return false
        val manager = context.getSystemService(CameraManager::class.java) ?: return false
        return try {
            manager.cameraIdList.any { id ->
                manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
        } catch (_: CameraAccessException) {
            // The camera service refused; the camera exists but is unusable right now.
            true
        }
    }

    override fun cameraDisabledByPolicy(): Boolean =
        context.getSystemService(DevicePolicyManager::class.java)?.getCameraDisabled(null) ?: false

    override fun hasLocationProvider(): Boolean {
        val manager = context.getSystemService(LocationManager::class.java) ?: return false
        val providers = manager.allProviders
        return LocationManager.GPS_PROVIDER in providers || LocationManager.NETWORK_PROVIDER in providers ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && LocationManager.FUSED_PROVIDER in providers)
    }

    override fun locationEnabled(): Boolean =
        context.getSystemService(LocationManager::class.java)?.let { LocationManagerCompat.isLocationEnabled(it) } ?: false

    override fun freeBytes(): Long = context.cacheDir.usableSpace

    override fun nativeLibraryAvailable(): Boolean {
        val fileName = System.mapLibraryName(NATIVE_LIBRARY)
        val extracted = context.applicationInfo.nativeLibraryDir?.let { File(it, fileName).exists() } ?: false
        if (extracted) return true
        val apks = listOfNotNull(context.applicationInfo.sourceDir) + context.applicationInfo.splitSourceDirs.orEmpty()
        val entries = Build.SUPPORTED_ABIS.map { abi -> "lib/$abi/$fileName" }
        return apks.any { apk -> containsAny(apk, entries) }
    }

    private fun containsAny(apk: String, entries: List<String>): Boolean = try {
        ZipFile(apk).use { zip -> entries.any { zip.getEntry(it) != null } }
    } catch (_: IOException) {
        false
    }

    private companion object {
        /** The OpenCV library the SDK needs (`libopencv_java4.so`). */
        const val NATIVE_LIBRARY = "opencv_java4"
    }
}
