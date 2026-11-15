package com.clearcapture.clearcapture.domain.port

import com.clearcapture.clearcapture.common.Outcome
import java.io.File

/** Where a kept photograph ends up, and how to reach it. */
data class StoredImage(val uri: String, val file: File?)

/** Why storing a kept photograph failed, in words for the worker's alert and the host's result. */
data class StoreFailure(val reason: String, val outOfSpace: Boolean)

/**
 * A place kept photographs are stored: app-private, the host's folder, the shared gallery or the host's own sink.
 * Implementations copy or move [source]; on failure [source] must be left untouched so the photo is never lost.
 */
fun interface ImageDestination {
    /** Stores [source] for [captureId], taken at [epochMillis]. */
    suspend fun store(source: File, captureId: String, epochMillis: Long): Outcome<StoredImage, StoreFailure>
}
