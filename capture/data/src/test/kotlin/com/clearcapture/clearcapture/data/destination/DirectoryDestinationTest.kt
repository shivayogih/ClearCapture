package com.clearcapture.clearcapture.data.destination

import com.clearcapture.clearcapture.common.Outcome
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DirectoryDestinationTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun source(name: String = "p.jpg") = File(temp.newFolder(), name).apply { writeBytes(ByteArray(100)) }

    @Test
    fun `SDK storage uses dated folders and moves the file`() = runTest {
        val root = File(temp.root, "files/cc_captures")
        val src = source()
        val stored = (DirectoryDestination(root, datedFolders = true, StandardTestDispatcher(testScheduler)).store(src, "abc", 1_763_000_000_000) as Outcome.Success).value
        assertThat(src.exists()).isFalse()
        assertThat(stored.file?.length()).isEqualTo(100)
        assertThat(stored.file?.invariantSeparatorsPath).matches(""".*/files/cc_captures/\d{4}/\d{2}/\d{2}/abc\.jpg""")
        assertThat(stored.uri).startsWith("file:")
    }

    @Test
    fun `the host folder is created if missing and used as it is`() = runTest {
        val root = File(temp.root, "jobs/4711")
        val stored = (DirectoryDestination(root, datedFolders = false, StandardTestDispatcher(testScheduler)).store(source(), "x", 0) as Outcome.Success).value
        assertThat(stored.file).isEqualTo(File(root, "x.jpg"))
    }

    @Test
    fun `a folder that cannot be created fails and leaves the source untouched`() = runTest {
        val blocker = File(temp.root, "blocker").apply { writeText("a file, not a folder") }
        val src = source()
        val result = DirectoryDestination(File(blocker, "sub"), datedFolders = false, StandardTestDispatcher(testScheduler)).store(src, "x", 0)
        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
        assertThat(src.exists()).isTrue()
        assertThat(DirectoryDestination(File(blocker, "sub"), false, StandardTestDispatcher(testScheduler)).isWritable()).isFalse()
    }
}
