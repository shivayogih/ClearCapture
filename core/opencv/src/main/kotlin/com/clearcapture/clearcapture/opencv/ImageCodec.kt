package com.clearcapture.clearcapture.opencv

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.os.Build
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.IOException

/**
 * Decodes JPEG pixels into mats. Both methods work in stored coordinates (EXIF orientation not applied)
 * and return `null` instead of throwing when the file cannot be decoded. The caller owns the returned mat.
 *
 * Android uses [AndroidImageCodec]; JVM unit tests use an `Imgcodecs`-based implementation.
 */
public interface ImageCodec {
    /** The whole image, decoded with subsampling by [sampleSize] (a power of two), as 8-bit BGR. */
    public fun decodeSubsampled(file: File, sampleSize: Int): Mat?

    /** [region] of the image at full resolution, as 8-bit grey. */
    public fun decodeRegionGray(file: File, region: Rect): Mat?
}

/** [ImageCodec] on `BitmapFactory` and `BitmapRegionDecoder`; the decoder only reads the region it needs. */
public class AndroidImageCodec : ImageCodec {

    override fun decodeSubsampled(file: File, sampleSize: Int): Mat? {
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeFile(file.path, options) ?: return null
        return try {
            convert(bitmap, Imgproc.COLOR_RGBA2BGR)
        } finally {
            bitmap.recycle()
        }
    }

    override fun decodeRegionGray(file: File, region: Rect): Mat? {
        val decoder = try {
            newRegionDecoder(file)
        } catch (_: IOException) {
            return null
        }
        return try {
            val bounds = android.graphics.Rect(region.x, region.y, region.x + region.width, region.y + region.height)
            val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            decoder.decodeRegion(bounds, options)?.let { bitmap ->
                try {
                    convert(bitmap, Imgproc.COLOR_RGBA2GRAY)
                } finally {
                    bitmap.recycle()
                }
            }
        } catch (_: IllegalArgumentException) {
            null
        } finally {
            decoder.recycle()
        }
    }

    private fun convert(bitmap: Bitmap, code: Int): Mat {
        val rgba = Mat()
        return try {
            Utils.bitmapToMat(bitmap, rgba)
            Mat().also { Imgproc.cvtColor(rgba, it, code) }
        } finally {
            rgba.release()
        }
    }

    private fun newRegionDecoder(file: File): BitmapRegionDecoder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            BitmapRegionDecoder.newInstance(file.path)
        } else {
            @Suppress("DEPRECATION") // The replacement needs API 31; minSdk is 26.
            BitmapRegionDecoder.newInstance(file.path, false)
        }
}
