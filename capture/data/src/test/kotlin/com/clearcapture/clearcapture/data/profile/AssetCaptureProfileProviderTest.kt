package com.clearcapture.clearcapture.data.profile

import com.clearcapture.clearcapture.common.Logger
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File

class AssetCaptureProfileProviderTest {

    private val bundled: String = File("src/main/assets/clearcapture/profiles.json").readText()
    private val errors = mutableListOf<String>()
    private val logger = object : Logger {
        override fun debug(tag: String, message: String) = Unit
        override fun warn(tag: String, message: String, error: Throwable?) = Unit
        override fun error(tag: String, message: String, error: Throwable?) {
            errors += message
        }
    }

    @Test
    fun `uses bundled profiles when the host ships none`() = runTest {
        val provider = AssetCaptureProfileProvider({ bundled }, { null }, StandardTestDispatcher(testScheduler), logger)
        assertThat(provider.profiles()).hasSize(4)
        assertThat(errors).isEmpty()
    }

    @Test
    fun `uses host profiles when they are valid`() = runTest {
        val host = bundled.replace("\"id\": \"general\"", "\"id\": \"site\"")
        val provider = AssetCaptureProfileProvider({ bundled }, { host }, StandardTestDispatcher(testScheduler), logger)
        assertThat(provider.profile("site")).isNotNull()
        assertThat(provider.profile("general")).isNull()
    }

    @Test
    fun `falls back to bundled profiles and logs when host file is bad`() = runTest {
        val provider = AssetCaptureProfileProvider({ bundled }, { "{ broken" }, StandardTestDispatcher(testScheduler), logger)
        assertThat(provider.profiles().map { it.id }).contains("container")
        assertThat(errors).hasSize(1)
    }

    @Test
    fun `loads once and caches`() = runTest {
        var reads = 0
        val provider = AssetCaptureProfileProvider(
            bundled = {
                reads++
                bundled
            },
            ioDispatcher = StandardTestDispatcher(testScheduler),
            logger = logger,
        )
        provider.profiles()
        provider.profile("person")
        provider.profilesBlocking()
        assertThat(reads).isEqualTo(1)
    }
}
