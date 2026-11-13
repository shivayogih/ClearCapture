package com.clearcapture.clearcapture.api

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.clearcapture.clearcapture.api.internal.SdkRuntime
import java.io.File

/** Entry point of the ClearCapture SDK. Every member is callable from Java. */
public object ClearCapture {

    @Volatile
    private var runtime: SdkRuntime? = null

    /**
     * Optional. Stores [config] for the next launches. Cheap: no disk, camera, location or OpenCV work.
     * Safe to call more than once; the last configuration before a launch wins.
     */
    @JvmStatic
    @JvmOverloads
    public fun initialize(context: Context, config: ClearCaptureConfig = ClearCaptureConfig()) {
        runtime = SdkRuntime(context, config)
    }

    /** True once [initialize] has been called, or a flow has been launched. */
    @JvmStatic
    public val isInitialized: Boolean get() = runtime != null

    /** The SDK version. */
    @JvmStatic
    public val sdkVersion: String get() = BuildInfo.VERSION

    /**
     * What blocks a capture right now, without showing anything or changing anything. Synchronous; it may read
     * the profile file and the app's APK on the calling thread.
     *
     * @param profileId when given, also checks that the profile exists and uses its location rule.
     */
    @JvmStatic
    @JvmOverloads
    public fun checkReadiness(context: Context, profileId: String? = null): Readiness =
        runtime(context).readinessChecker.check(profileId)

    /** A `content://` URI for an accepted photograph, from the SDK's file provider, to hand to another app. */
    @JvmStatic
    public fun getShareableUri(context: Context, imageUri: Uri): Uri {
        val path = requireNotNull(imageUri.path) { "imageUri must be a file:// URI returned by the SDK" }
        return FileProvider.getUriForFile(context, context.packageName + FILE_PROVIDER_SUFFIX, File(path))
    }

    internal fun runtime(context: Context): SdkRuntime =
        runtime ?: synchronized(this) { runtime ?: SdkRuntime(context, ClearCaptureConfig()).also { runtime = it } }

    internal const val FILE_PROVIDER_SUFFIX: String = ".clearcapture.files"
}
