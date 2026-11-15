package com.clearcapture.clearcapture.api.internal

import android.util.Log
import com.clearcapture.clearcapture.api.spi.CaptureLogger
import com.clearcapture.clearcapture.common.Logger

/** Routes the SDK's internal [Logger] to the host's [CaptureLogger]. */
internal class HostLogger(private val host: CaptureLogger) : Logger {
    override fun debug(tag: String, message: String) = host.debug(tag, message)
    override fun warn(tag: String, message: String, error: Throwable?) = host.warn(tag, message, error)
    override fun error(tag: String, message: String, error: Throwable?) = host.error(tag, message, error)
}

/** Logcat logger, used by default only when the host app is debuggable. */
internal object LogcatLogger : Logger {
    override fun debug(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun warn(tag: String, message: String, error: Throwable?) {
        Log.w(tag, message, error)
    }

    override fun error(tag: String, message: String, error: Throwable?) {
        Log.e(tag, message, error)
    }
}
