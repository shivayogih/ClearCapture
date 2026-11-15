package com.clearcapture.clearcapture.data.file

import com.clearcapture.clearcapture.common.Clock
import com.clearcapture.clearcapture.common.Logger
import com.clearcapture.clearcapture.common.Outcome
import com.clearcapture.clearcapture.domain.model.CaptureError
import com.clearcapture.clearcapture.domain.port.FileStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * App-private photograph storage (LLD section 9): pending in `cacheDir/cc_pending/{id}.jpg`, accepted in
 * `filesDir/cc_captures/yyyy/MM/dd/{id}.jpg` or under the host's output directory. Nothing goes to shared storage.
 */
class AppFileStore(
    cacheDir: File,
    filesDir: File,
    outputDirectory: File?,
    private val ioDispatcher: CoroutineDispatcher,
    private val clock: Clock,
    private val logger: Logger = Logger.None,
) : FileStore {

    private val pendingDir = File(cacheDir, "cc_pending")
    private val acceptedRoot = outputDirectory ?: File(filesDir, "cc_captures")
    private val datePath = SimpleDateFormat("yyyy/MM/dd", Locale.US)

    override fun newPendingFile(captureId: String): File = File(pendingDir, "$captureId.jpg").also { pendingDir.mkdirs() }

    override suspend fun promote(pending: File, captureId: String, epochMillis: Long): Outcome<File, CaptureError> =
        withContext(ioDispatcher) {
            val target = File(File(acceptedRoot, datePath.format(Date(epochMillis))), "$captureId.jpg")
            try {
                target.parentFile?.mkdirs()
                if (pending.renameTo(target)) return@withContext Outcome.Success(target)
                // Different volume: copy, verify the size, then delete the original.
                pending.copyTo(target, overwrite = true)
                if (target.length() != pending.length()) {
                    target.delete()
                    return@withContext Outcome.Failure(CaptureError.StorageFull)
                }
                pending.delete()
                Outcome.Success(target)
            } catch (e: IOException) {
                logger.error(TAG, "Could not promote a capture", e)
                target.delete()
                Outcome.Failure(CaptureError.StorageFull)
            }
        }

    override suspend fun delete(file: File) {
        withContext(ioDispatcher) { file.delete() }
    }

    override fun freeBytes(): Long = pendingDir.parentFile?.usableSpace ?: 0L

    /** Pending files are never referenced by an accepted record, so any old one is an orphan. */
    override suspend fun deleteOrphans(maxAgeMillis: Long) = withContext(ioDispatcher) {
        val cutoff = clock.nowMillis() - maxAgeMillis
        pendingDir.listFiles()?.filter { it.isFile && it.lastModified() < cutoff }?.forEach { it.delete() }
        Unit
    }

    private companion object {
        const val TAG = "CcFiles"
    }
}
