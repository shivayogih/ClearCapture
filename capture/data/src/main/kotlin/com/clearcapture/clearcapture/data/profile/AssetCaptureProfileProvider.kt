package com.clearcapture.clearcapture.data.profile

import com.clearcapture.clearcapture.common.Logger
import com.clearcapture.clearcapture.common.Outcome
import com.clearcapture.clearcapture.domain.model.CaptureProfile
import com.clearcapture.clearcapture.domain.port.CaptureProfileProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Loads profiles from JSON, once, and caches them. [primary] is the host's JSON when it ships one;
 * if it is absent or invalid, [bundled] (the SDK's own asset) is used and an error is logged.
 *
 * Text sources are plain functions so this class needs no Android types and is tested on the JVM.
 */
class AssetCaptureProfileProvider(
    private val bundled: () -> String,
    private val primary: (() -> String?)? = null,
    private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger = Logger.None,
) : CaptureProfileProvider {

    private val mutex = Mutex()
    private var cache: List<CaptureProfile>? = null

    override suspend fun profiles(): List<CaptureProfile> = mutex.withLock {
        cache ?: withContext(ioDispatcher) { load() }.also { cache = it }
    }

    override suspend fun profile(id: String): CaptureProfile? = profiles().firstOrNull { it.id == id }

    /** Loads synchronously on the calling thread; for `checkReadiness`, which must be synchronous. */
    fun profilesBlocking(): List<CaptureProfile> = cache ?: load().also { cache = it }

    private fun load(): List<CaptureProfile> {
        val hostText = primary?.let { readSafely(it, "host profile JSON") }
        if (hostText != null) {
            when (val parsed = ProfileParser.parse(hostText)) {
                is Outcome.Success -> return parsed.value
                is Outcome.Failure -> logger.error(TAG, "Host profiles rejected, using the bundled defaults: ${parsed.error}")
            }
        }
        val bundledText = readSafely(bundled, "bundled profile JSON") ?: return emptyList()
        return when (val parsed = ProfileParser.parse(bundledText)) {
            is Outcome.Success -> parsed.value
            is Outcome.Failure -> {
                logger.error(TAG, "Bundled profiles are invalid: ${parsed.error}")
                emptyList()
            }
        }
    }

    private fun readSafely(source: () -> String?, what: String): String? = try {
        source()
    } catch (e: java.io.IOException) {
        logger.error(TAG, "Could not read $what", e)
        null
    }

    /** Asset paths used by the SDK. */
    companion object {
        /** The SDK's own profile file inside its assets. */
        const val BUNDLED_ASSET: String = "clearcapture/profiles.json"

        /** A host can ship its own profiles at this asset path instead of supplying a provider. */
        const val HOST_ASSET: String = "clearcapture_profiles.json"

        private const val TAG = "CcProfiles"
    }
}
