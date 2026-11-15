package com.clearcapture.clearcapture.data.file

import com.clearcapture.clearcapture.common.Clock
import com.clearcapture.clearcapture.domain.port.FileStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Pending photographs (LLD section 9): `cacheDir/cc_pending/{id}.jpg` until they are kept or discarded. Kept photos
 * move on through an image destination; nothing here goes to shared storage.
 */
class AppFileStore(
    cacheDir: File,
    private val ioDispatcher: CoroutineDispatcher,
    private val clock: Clock,
) : FileStore {

    private val pendingDir = File(cacheDir, "cc_pending")

    override fun newPendingFile(captureId: String): File = File(pendingDir, "$captureId.jpg").also { pendingDir.mkdirs() }

    override suspend fun delete(file: File) {
        withContext(ioDispatcher) { file.delete() }
    }

    override fun freeBytes(): Long = pendingDir.parentFile?.usableSpace ?: 0L

    /** Pending files never belong to a kept photo, so any old one is an orphan. */
    override suspend fun deleteOrphans(maxAgeMillis: Long) = withContext(ioDispatcher) {
        val cutoff = clock.nowMillis() - maxAgeMillis
        pendingDir.listFiles()?.filter { it.isFile && it.lastModified() < cutoff }?.forEach { it.delete() }
        Unit
    }
}
