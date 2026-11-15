package com.clearcapture.clearcapture.opencv

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader

/** Loads the OpenCV native library. Returns `true` when the library is usable. May throw a [LinkageError]. */
public fun interface OpenCvLibraryLoader {
    /** Loads the library; called at most once per [OpenCvInitializer]. */
    public fun load(): Boolean

    /** Loaders provided by this module. */
    public companion object {
        /** Loads `libopencv_java4.so` packaged with the Android AAR. */
        public val Android: OpenCvLibraryLoader = OpenCvLibraryLoader { OpenCVLoader.initLocal() }
    }
}

/** Whether the OpenCV native library is usable. */
public sealed interface OpenCvStatus {
    /** [OpenCvInitializer.initialize] has not completed yet. */
    public data object NotLoaded : OpenCvStatus

    /** The library is loaded; OpenCV calls are safe. */
    public data object Loaded : OpenCvStatus

    /** Loading failed; OpenCV calls must not be made. [cause] is for logs only. */
    public data class Failed(val cause: String) : OpenCvStatus
}

/**
 * Loads OpenCV once per process, off the caller's thread.
 *
 * [initialize] is idempotent and thread-safe: concurrent callers wait for the same attempt and the first
 * attempt's result is kept. A failure is returned as [OpenCvStatus.Failed], never thrown; `:core:quality`
 * reports it as `CaptureError.AnalyzerUnavailable` (this module has no domain dependency, decision D5).
 */
public class OpenCvInitializer(
    private val dispatcher: CoroutineDispatcher,
    private val loader: OpenCvLibraryLoader = OpenCvLibraryLoader.Android,
) {
    private val mutex = Mutex()

    @Volatile
    private var current: OpenCvStatus = OpenCvStatus.NotLoaded

    /** The current status, without waiting. */
    public val status: OpenCvStatus get() = current

    /** True once the library has loaded successfully. */
    public val isLoaded: Boolean get() = current == OpenCvStatus.Loaded

    /** Loads the library on the injected dispatcher, or returns the result of the earlier attempt. */
    public suspend fun initialize(): OpenCvStatus {
        val known = current
        if (known != OpenCvStatus.NotLoaded) return known
        return mutex.withLock {
            if (current == OpenCvStatus.NotLoaded) {
                current = withContext(dispatcher) { loadSafely() }
            }
            current
        }
    }

    private fun loadSafely(): OpenCvStatus = try {
        if (loader.load()) OpenCvStatus.Loaded else OpenCvStatus.Failed("The OpenCV loader reported failure")
    } catch (error: LinkageError) {
        OpenCvStatus.Failed("${error::class.java.simpleName}: ${error.message}")
    } catch (error: SecurityException) {
        OpenCvStatus.Failed("SecurityException: ${error.message}")
    }
}
