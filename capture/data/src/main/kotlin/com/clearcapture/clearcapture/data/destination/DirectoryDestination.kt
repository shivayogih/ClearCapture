package com.clearcapture.clearcapture.data.destination

import com.clearcapture.clearcapture.common.Outcome
import com.clearcapture.clearcapture.domain.port.ImageDestination
import com.clearcapture.clearcapture.domain.port.StoreFailure
import com.clearcapture.clearcapture.domain.port.StoredImage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Stores kept photographs in a folder: the SDK's own `files/cc_captures` (with `yyyy/MM/dd` subfolders) or the
 * host's folder. Rename when possible, otherwise copy, verify the size and delete. On failure the source is untouched.
 */
class DirectoryDestination(
    private val root: File,
    private val datedFolders: Boolean,
    private val ioDispatcher: CoroutineDispatcher,
) : ImageDestination {

    override suspend fun store(source: File, captureId: String, epochMillis: Long): Outcome<StoredImage, StoreFailure> =
        withContext(ioDispatcher) {
            val folder = if (datedFolders) File(root, SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(epochMillis))) else root
            val target = File(folder, "$captureId.jpg")
            try {
                if (!folder.isDirectory && !folder.mkdirs()) return@withContext failure("Cannot create the folder ${folder.path}")
                if (!folder.canWrite()) return@withContext failure("The folder ${folder.path} is not writable")
                if (folder.usableSpace < source.length()) {
                    return@withContext Outcome.Failure(StoreFailure("No space left", outOfSpace = true))
                }
                if (!source.renameTo(target)) {
                    // Different volume: copy, verify, then remove the original.
                    source.copyTo(target, overwrite = true)
                    if (target.length() != source.length()) {
                        target.delete()
                        return@withContext failure("The copy in ${folder.path} is incomplete")
                    }
                    source.delete()
                }
                Outcome.Success(StoredImage(target.toURI().toString(), target))
            } catch (e: IOException) {
                target.takeIf { source.exists() }?.delete()
                val full = e.message?.contains("ENOSPC") == true || e.message?.contains("No space") == true
                Outcome.Failure(StoreFailure(e.message ?: "Write failed", outOfSpace = full))
            } catch (e: SecurityException) {
                failure(e.message ?: "Access denied to ${folder.path}")
            }
        }

    /** True when the destination folder can be created and written, for the check when a session opens. */
    fun isWritable(): Boolean = try {
        (root.isDirectory || root.mkdirs()) && root.canWrite()
    } catch (_: SecurityException) {
        false
    }

    private fun failure(reason: String) = Outcome.Failure(StoreFailure(reason, outOfSpace = false))
}
