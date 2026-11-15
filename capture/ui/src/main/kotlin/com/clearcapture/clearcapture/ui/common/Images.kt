package com.clearcapture.clearcapture.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream

/** Decodes a downsampled, upright preview of a photograph off the main thread. Never the full-resolution image. */
@Composable
internal fun rememberPreview(uri: String?, maxSide: Int): State<ImageBitmap?> {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(null, uri, maxSide) {
        value = uri?.let { withContext(Dispatchers.IO) { decode(context, Uri.parse(it), maxSide) } }
    }
}

private fun decode(context: Context, uri: Uri, maxSide: Int): ImageBitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    open(context, uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val bitmap = open(context, uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    val degrees = open(context, uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
    bitmap?.let { rotate(it, degrees).asImageBitmap() }
} catch (_: IOException) {
    null
} catch (_: SecurityException) {
    null
}

private fun open(context: Context, uri: Uri): InputStream? =
    if (uri.scheme == "file") uri.path?.let { java.io.File(it).inputStream() } else context.contentResolver.openInputStream(uri)

private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap = if (degrees == 0) {
    bitmap
} else {
    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
}
