package com.clearcapture.clearcapture.opencv

import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfInt
import org.opencv.core.Rect
import org.opencv.imgcodecs.Imgcodecs
import java.io.File

/** Loads the desktop natives (org.openpnp) once per JVM. */
object DesktopNatives {
    private val loaded by lazy { nu.pattern.OpenCV.loadLocally() }

    /** Loads the natives. */
    fun load() = loaded
}

/** [ImageCodec] on OpenCV's codecs for the JVM; ignores EXIF orientation like `BitmapFactory`. */
class JvmImageCodec : ImageCodec {
    override fun decodeSubsampled(file: File, sampleSize: Int): Mat? {
        val flags = when (sampleSize) {
            1 -> Imgcodecs.IMREAD_COLOR
            2 -> Imgcodecs.IMREAD_REDUCED_COLOR_2
            4 -> Imgcodecs.IMREAD_REDUCED_COLOR_4
            else -> Imgcodecs.IMREAD_REDUCED_COLOR_8
        } or Imgcodecs.IMREAD_IGNORE_ORIENTATION
        return Imgcodecs.imread(file.path, flags).takeUnless { it.empty() }
    }

    override fun decodeRegionGray(file: File, region: Rect): Mat? {
        val image = Imgcodecs.imread(file.path, Imgcodecs.IMREAD_GRAYSCALE or Imgcodecs.IMREAD_IGNORE_ORIENTATION)
        if (image.empty()) return null
        return image.submat(region).clone().also { image.release() }
    }
}

/** JPEG bytes for tests, optionally with an EXIF orientation tag. */
object TestJpegs {
    /** Encodes [image] as JPEG. */
    fun encode(image: Mat, quality: Int = 90): ByteArray {
        val out = MatOfByte()
        val params = MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, quality)
        check(Imgcodecs.imencode(".jpg", image, out, params))
        return out.toArray().also {
            out.release()
            params.release()
        }
    }

    /** [jpeg] with an APP1 Exif segment holding only IFD0 orientation [orientation], right after SOI. */
    fun withOrientation(jpeg: ByteArray, orientation: Int, bigEndian: Boolean = false): ByteArray {
        fun u16(value: Int) = if (bigEndian) {
            byteArrayOf((value shr 8).toByte(), value.toByte())
        } else {
            byteArrayOf(value.toByte(), (value shr 8).toByte())
        }
        fun u32(value: Int) = if (bigEndian) u16(value shr 16) + u16(value) else u16(value) + u16(value shr 16)
        val tiff = (if (bigEndian) "MM" else "II").toByteArray() + u16(42) + u32(8) +
            u16(1) + u16(0x0112) + u16(3) + u32(1) + u16(orientation) + u16(0) + u32(0)
        val payload = "Exif".toByteArray() + byteArrayOf(0, 0) + tiff
        val length = payload.size + 2
        val segment = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (length shr 8).toByte(), length.toByte()) + payload
        return jpeg.copyOfRange(0, 2) + segment + jpeg.copyOfRange(2, jpeg.size)
    }
}
