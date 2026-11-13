package com.clearcapture.clearcapture.api.internal

import android.content.Context
import android.content.pm.ApplicationInfo
import com.clearcapture.clearcapture.api.ClearCaptureConfig
import com.clearcapture.clearcapture.api.internal.readiness.AndroidReadinessEnvironment
import com.clearcapture.clearcapture.api.internal.readiness.ReadinessChecker
import com.clearcapture.clearcapture.common.AppDispatchers
import com.clearcapture.clearcapture.common.Logger
import com.clearcapture.clearcapture.data.profile.AssetCaptureProfileProvider
import kotlinx.coroutines.Dispatchers
import java.io.FileNotFoundException

/**
 * Process-scoped part of the graph (LLD section 10): configuration, logger and profile provider.
 * Everything is created lazily on first use; nothing runs at app start.
 */
internal class SdkRuntime(context: Context, val config: ClearCaptureConfig) {

    val appContext: Context = context.applicationContext

    val dispatchers: AppDispatchers by lazy {
        AppDispatchers(main = Dispatchers.Main.immediate, io = Dispatchers.IO, default = Dispatchers.Default)
    }

    val logger: Logger by lazy {
        config.dependencies.logger?.let(::HostLogger)
            ?: if (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) LogcatLogger else Logger.None
    }

    val profileProvider: AssetCaptureProfileProvider by lazy {
        val host = config.dependencies.profileProvider
        AssetCaptureProfileProvider(
            bundled = { readAsset(AssetCaptureProfileProvider.BUNDLED_ASSET) ?: "" },
            primary = { host?.profilesJson() ?: readAsset(AssetCaptureProfileProvider.HOST_ASSET) },
            ioDispatcher = dispatchers.io,
            logger = logger,
        )
    }

    val readinessChecker: ReadinessChecker by lazy {
        ReadinessChecker(AndroidReadinessEnvironment(appContext)) { profileProvider.profilesBlocking() }
    }

    private fun readAsset(path: String): String? = try {
        appContext.assets.open(path).bufferedReader().use { it.readText() }
    } catch (_: FileNotFoundException) {
        null
    }
}
