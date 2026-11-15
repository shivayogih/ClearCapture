package com.clearcapture.clearcapture.data.file

import com.clearcapture.clearcapture.domain.fakes.FakeClock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppFileStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val clock = FakeClock(now = 1_763_000_000_000)

    @Test
    fun `pending files live in the cache`() = runTest {
        val store = AppFileStore(temp.newFolder("cache"), StandardTestDispatcher(testScheduler), clock)
        assertThat(store.newPendingFile("abc").path).contains("cc_pending")
    }

    @Test
    fun `orphans older than the limit are deleted, recent ones kept`() = runTest {
        val store = AppFileStore(temp.newFolder("cache"), StandardTestDispatcher(testScheduler), clock)
        val old = store.newPendingFile("old").apply { writeBytes(ByteArray(1)); setLastModified(clock.now - 25 * 3_600_000L) }
        val fresh = store.newPendingFile("fresh").apply { writeBytes(ByteArray(1)); setLastModified(clock.now - 3_600_000L) }
        store.deleteOrphans(24 * 3_600_000L)
        assertThat(old.exists()).isFalse()
        assertThat(fresh.exists()).isTrue()
    }
}
