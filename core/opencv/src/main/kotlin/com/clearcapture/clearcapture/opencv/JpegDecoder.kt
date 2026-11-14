package com.clearcapture.clearcapture.opencv

import org.opencv.core.CvException
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import kotlin.math.roundToInt

/**
 * The images post-capture analysis works on (LLD 5.0). The caller owns both mats;
 * [FrameMats.fromDecoded] takes them over.
 */
public class DecodedImage(
    /** The whole photograph, upright, 8-bit BGR, long side about [JpegDecoder.targetLongSide] (never upscaled). */
    public val analysisBgr: Mat,
    /** A full-resolution grey crop of up to [JpegDecoder.cropSize] square, centred on the guide, upright. */
    public val sharpCrop: Mat?,
)

/**
 * Decodes a saved JPEG once into the normalised analysis image and the full-resolution `sharpCrop`.
 *
 * The analysis image is decoded with the largest power-of-two subsampling that keeps the long side at or
 * above [targetLongSide], then scaled down to [targetLongSide] if it is still larger. Normalising the size
 * keeps memory bounded on high-megapixel cameras and makes the blur thresholds comparable between cameras.
 */
public class JpegDecoder(
    private val codec: ImageCodec,
    /** Long side of the analysis image in pixels. */
    public val targetLongSide: Int = DEFAULT_TARGET_LONG_SIDE,
    /** Side of the square full-resolution crop for the blur check. */
    public val cropSize: Int = DEFAULT_CROP_SIZE,
) {

    /**
     * Decodes [file], whose structure is described by [header]. [guide] positions the crop; the crop is
     * centred on the image when it is `null`. Returns `null` when the pixels cannot be decoded.
     */
    public fun decode(file: File, header: JpegHeader, guide: NormalizedRect?): DecodedImage? {
        val stored = codec.decodeSubsampled(file, sampleSize(maxOf(header.width, header.height))) ?: return null
        val analysis = try {
            normalise(stored, header.orientation)
        } finally {
            stored.release()
        }
        // Without the crop the blur check falls back to the guide region of the analysis image.
        val crop = try {
            decodeCrop(file, header, guide)
        } catch (_: CvException) {
            null
        }
        return DecodedImage(analysis, crop)
    }

    /** The subsampling factor for a stored long side of [longSide] pixels. */
    public fun sampleSize(longSide: Int): Int {
        var sample = 1
        while (longSide / (sample * 2) >= targetLongSide) sample *= 2
        return sample
    }

    private fun normalise(stored: Mat, orientation: Int): Mat {
        val longSide = maxOf(stored.cols(), stored.rows())
        val scaled = if (longSide > targetLongSide) {
            val factor = targetLongSide.toDouble() / longSide
            val size = Size((stored.cols() * factor).roundToInt().toDouble(), (stored.rows() * factor).roundToInt().toDouble())
            Mat().also { Imgproc.resize(stored, it, size, 0.0, 0.0, Imgproc.INTER_AREA) }
        } else {
            stored
        }
        return try {
            Mat().also { ExifOrientation.toUpright(scaled, orientation, it) }
        } finally {
            if (scaled !== stored) scaled.release()
        }
    }

    private fun decodeCrop(file: File, header: JpegHeader, guide: NormalizedRect?): Mat? {
        val width = header.uprightWidth
        val height = header.uprightHeight
        val centreX = guide?.let { (it.left + it.right) / 2f } ?: HALF
        val centreY = guide?.let { (it.top + it.bottom) / 2f } ?: HALF
        val cropWidth = minOf(cropSize, width)
        val cropHeight = minOf(cropSize, height)
        val x = ((centreX * width).roundToInt() - cropWidth / 2).coerceIn(0, width - cropWidth)
        val y = ((centreY * height).roundToInt() - cropHeight / 2).coerceIn(0, height - cropHeight)
        val upright = Rect(x, y, cropWidth, cropHeight)
        val storedRect = ExifOrientation.uprightRectToStored(upright, header.orientation, width, height)
        val region = codec.decodeRegionGray(file, storedRect) ?: return null
        return try {
            Mat().also { ExifOrientation.toUpright(region, header.orientation, it) }
        } finally {
            region.release()
        }
    }

    private companion object {
        const val DEFAULT_TARGET_LONG_SIDE = 2000
        const val DEFAULT_CROP_SIZE = 1024
        const val HALF = 0.5f
    }
}
