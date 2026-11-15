package com.clearcapture.clearcapture.data.destination

import android.content.ContentResolver
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.clearcapture.clearcapture.common.Outcome
import com.clearcapture.clearcapture.domain.port.ImageDestination
import com.clearcapture.clearcapture.domain.port.StoreFailure
import com.clearcapture.clearcapture.domain.port.StoredImage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Stores kept photographs in the shared gallery under `Pictures/<album>` (StorageTarget.SharedPictures). The bytes are
 * copied unchanged, so EXIF and GPS tags are kept. Android 10+ needs no permission; on Android 8-9 the host must hold
 * WRITE_EXTERNAL_STORAGE, otherwise the insert fails and the photo stays in pending storage.
 */
class MediaStoreDestination(
    private val resolver: ContentResolver,
    private val album: String,
    private val ioDispatcher: CoroutineDispatcher,
) : ImageDestination {

    override suspend fun store(source: File, captureId: String, epochMillis: Long): Outcome<StoredImage, StoreFailure> =
        withContext(ioDispatcher) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$captureId.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.DATE_TAKEN, epochMillis)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$album")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val uri = try {
                resolver.insert(collection, values)
            } catch (e: SecurityException) {
                return@withContext Outcome.Failure(StoreFailure(e.message ?: "The gallery refused the photo", outOfSpace = false))
            } ?: return@withContext Outcome.Failure(StoreFailure("The gallery refused the photo", outOfSpace = false))
            try {
                resolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } }
                    ?: throw IOException("Cannot write to the gallery")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                }
                source.delete()
                Outcome.Success(StoredImage(uri.toString(), null))
            } catch (e: IOException) {
                resolver.delete(uri, null, null)
                val full = e.message?.contains("ENOSPC") == true || e.message?.contains("No space") == true
                Outcome.Failure(StoreFailure(e.message ?: "Write to the gallery failed", outOfSpace = full))
            }
        }
}
