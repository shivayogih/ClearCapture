package com.clearcapture.clearcapture.opencv

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class OpenCvInitializerTest {

    @Test
    fun `loads once however often and from however many threads it is called`() = runBlocking {
        val loads = AtomicInteger()
        val initializer = OpenCvInitializer(Dispatchers.Default) {
            loads.incrementAndGet()
            Thread.sleep(20)
            true
        }
        val results = (1..16).map { async(Dispatchers.Default) { initializer.initialize() } }.awaitAll()
        assertThat(results.toSet()).containsExactly(OpenCvStatus.Loaded)
        assertThat(initializer.initialize()).isEqualTo(OpenCvStatus.Loaded)
        assertThat(loads.get()).isEqualTo(1)
        assertThat(initializer.isLoaded).isTrue()
    }

    @Test
    fun `starts as not loaded and runs the loader on the injected dispatcher`() = runTest {
        var loaderThread: String? = null
        val initializer = OpenCvInitializer(StandardTestDispatcher(testScheduler)) {
            loaderThread = Thread.currentThread().name
            true
        }
        assertThat(initializer.status).isEqualTo(OpenCvStatus.NotLoaded)
        initializer.initialize()
        assertThat(loaderThread).isNotNull()
    }

    @Test
    fun `a loader that returns false is a failure, kept for later calls`() = runTest {
        val loads = AtomicInteger()
        val initializer = OpenCvInitializer(StandardTestDispatcher(testScheduler)) {
            loads.incrementAndGet()
            false
        }
        assertThat(initializer.initialize()).isInstanceOf(OpenCvStatus.Failed::class.java)
        assertThat(initializer.initialize()).isInstanceOf(OpenCvStatus.Failed::class.java)
        assertThat(loads.get()).isEqualTo(1)
        assertThat(initializer.isLoaded).isFalse()
    }

    @Test
    fun `a linkage error is reported, never thrown`() = runTest {
        val unsatisfied = OpenCvInitializer(StandardTestDispatcher(testScheduler)) { throw UnsatisfiedLinkError("no lib") }
        val missingClass = OpenCvInitializer(StandardTestDispatcher(testScheduler)) { throw NoClassDefFoundError("OpenCVLoader") }
        val status = unsatisfied.initialize()
        assertThat(status).isInstanceOf(OpenCvStatus.Failed::class.java)
        assertThat((status as OpenCvStatus.Failed).cause).contains("UnsatisfiedLinkError")
        assertThat(missingClass.initialize()).isInstanceOf(OpenCvStatus.Failed::class.java)
    }

    @Test
    fun `the Android loader fails cleanly on the JVM, where the AAR is absent`() = runTest {
        val status = OpenCvInitializer(StandardTestDispatcher(testScheduler)).initialize()
        assertThat(status).isInstanceOf(OpenCvStatus.Failed::class.java)
    }
}
