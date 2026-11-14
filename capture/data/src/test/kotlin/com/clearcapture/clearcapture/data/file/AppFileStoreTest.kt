package com.clearcapture.clearcapture.data.file

import com.clearcapture.clearcapture.common.Outcome
import com.clearcapture.clearcapture.domain.fakes.FakeClock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AppFileStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val clock = FakeClock(now = 1_763_000_000_000)

    private fun store(output: File? = null, dispatcher: TestDispatcher) =
        AppFileStore(temp.newFolder("cache"), temp.newFolder("files"), output, dispatcher, clock)

    @Test
    fun `pending files live in the cache and are promoted by date`() = runTest {
        val store = store(dispatcher = StandardTestDispatcher(testScheduler))
        val pending = store.newPendingFile("abc").apply { writeBytes(ByteArray(100)) }
        assertThat(pending.path).contains("cc_pending")

        val promoted = (store.promote(pending, "abc", clock.now) as Outcome.Success).value

        assertThat(pending.exists()).isFalse()
        assertThat(promoted.length()).isEqualTo(100)
        assertThat(promoted.invariantSeparatorsPath).matches(".*/files/cc_captures/\\d{4}/\\d{2}/\\d{2}/abc\\.jpg")
    }

    @Test
    fun `host output directory is used when given`() = runTest {
        val output = temp.newFolder("host")
        val store = store(output, StandardTestDispatcher(testScheduler))
        val pending = store.newPendingFile("x").apply { writeBytes(ByteArray(10)) }
        val promoted = (store.promote(pending, "x", clock.now) as Outcome.Success).value
        assertThat(promoted.path).startsWith(output.path)
    }

    @Test
    fun `orphans older than the limit are deleted, recent ones kept`() = runTest {
        val store = store(dispatcher = StandardTestDispatcher(testScheduler))
        val old = store.newPendingFile("old").apply { writeBytes(ByteArray(1)); setLastModified(clock.now - 25 * 3_600_000L) }
        val fresh = store.newPendingFile("fresh").apply { writeBytes(ByteArray(1)); setLastModified(clock.now - 3_600_000L) }

        store.deleteOrphans(24 * 3_600_000L)

        assertThat(old.exists()).isFalse()
        assertThat(fresh.exists()).isTrue()
    }

    @Test
    fun `delete removes the file`() = runTest {
        val store = store(dispatcher = StandardTestDispatcher(testScheduler))
        val file = store.newPendingFile("d").apply { writeBytes(ByteArray(1)) }
        store.delete(file)
        assertThat(file.exists()).isFalse()
    }
}
