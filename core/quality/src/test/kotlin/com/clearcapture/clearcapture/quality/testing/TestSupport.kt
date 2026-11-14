package com.clearcapture.clearcapture.quality.testing

import com.clearcapture.clearcapture.domain.model.FrameData
import com.clearcapture.clearcapture.opencv.ImageCodec
import com.clearcapture.clearcapture.opencv.OpenCvLibraryLoader
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.File
import java.nio.ByteBuffer

/** Desktop OpenCV natives (org.openpnp) for JVM tests. */
object DesktopOpenCv {
    /** Loads the natives for this platform; safe to call repeatedly. */
    val loader: OpenCvLibraryLoader = OpenCvLibraryLoader {
        load()
        true
    }

    private val loaded by lazy { nu.pattern.OpenCV.loadLocally() }

    /** Loads the natives once per JVM. */
    fun load() = loaded
}

/** [ImageCodec] on OpenCV's own codecs, for the JVM. Region decoding decodes everything and crops. */
class ImgcodecsImageCodec : ImageCodec {
    override fun decodeSubsampled(file: File, sampleSize: Int): Mat? {
        val reduced = when (sampleSize) {
            1 -> Imgcodecs.IMREAD_COLOR
            2 -> Imgcodecs.IMREAD_REDUCED_COLOR_2
            4 -> Imgcodecs.IMREAD_REDUCED_COLOR_4
            else -> Imgcodecs.IMREAD_REDUCED_COLOR_8
        }
        val image = Imgcodecs.imread(file.path, reduced or Imgcodecs.IMREAD_IGNORE_ORIENTATION)
        return image.takeUnless { it.empty() }
    }

    override fun decodeRegionGray(file: File, region: Rect): Mat? {
        val image = Imgcodecs.imread(file.path, Imgcodecs.IMREAD_GRAYSCALE or Imgcodecs.IMREAD_IGNORE_ORIENTATION)
        if (image.empty()) return null
        return try {
            image.submat(region).clone()
        } finally {
            image.release()
        }
    }
}

/** The committed fixture images (decision D22). */
object Fixtures {
    const val SHARP = "sharp.jpg"
    const val MOTION_BLUR = "motion_blur.jpg"
    const val DEFOCUS = "defocus.jpg"
    const val DARK = "dark.jpg"
    const val OVEREXPOSED = "overexposed.jpg"
    const val GLARE = "glare.jpg"
    const val CUT_OFF = "cut_off.jpg"
    const val TOO_SMALL = "too_small.jpg"
    const val OBSTRUCTED = "obstructed.jpg"
    const val LOW_RESOLUTION = "low_resolution.jpg"
    const val TRUNCATED = "truncated.jpg"

    /** The fixture file on the test classpath. */
    fun file(name: String): File {
        val url = checkNotNull(Fixtures::class.java.classLoader?.getResource("fixtures/$name")) { "Missing fixture $name" }
        return File(url.toURI())
    }

    /** The fixture decoded as BGR (upright; the fixtures carry no orientation tag). */
    fun bgr(name: String): Mat = Imgcodecs.imread(file(name).path, Imgcodecs.IMREAD_COLOR).also { check(!it.empty()) }
}

/** Builds camera-like live frames from images. */
object LiveFrames {
    const val WIDTH = 640
    const val HEIGHT = 480

    /**
     * [upright] scaled to [width] × [height] and packed as YUV_420_888 in direct buffers. With
     * [interleaved] the chroma planes have a pixel stride of 2, as on most phones; otherwise 1.
     * With [rotationDegrees] the frame is stored rotated so that rotating it by that amount restores [upright].
     */
    fun fromBgr(
        upright: Mat,
        interleaved: Boolean = false,
        rotationDegrees: Int = 0,
        width: Int = WIDTH,
        height: Int = HEIGHT,
    ): FrameData {
        val scaled = Mat()
        val sensor = Mat()
        val yuv = Mat()
        try {
            Imgproc.resize(upright, scaled, Size(width.toDouble(), height.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
            when (rotationDegrees) {
                90 -> Core.rotate(scaled, sensor, Core.ROTATE_90_COUNTERCLOCKWISE)
                180 -> Core.rotate(scaled, sensor, Core.ROTATE_180)
                270 -> Core.rotate(scaled, sensor, Core.ROTATE_90_CLOCKWISE)
                else -> scaled.copyTo(sensor)
            }
            val w = sensor.cols()
            val h = sensor.rows()
            // Full-range JFIF YCbCr with 2×2 subsampled chroma, as camera YUV_420_888 delivers.
            Imgproc.cvtColor(sensor, yuv, Imgproc.COLOR_BGR2YCrCb)
            val planes = ArrayList<Mat>()
            Core.split(yuv, planes)
            val y = direct(ByteArray(w * h).also { planes[0].get(0, 0, it) })
            val v = subsample(planes[1], w, h)
            val u = subsample(planes[2], w, h)
            planes.forEach(Mat::release)
            return if (interleaved) {
                FrameData(w, h, rotationDegrees, y, w, direct(interleave(u, v)), direct(interleave(v, u)), w, 2, 0L)
            } else {
                FrameData(w, h, rotationDegrees, y, w, direct(u), direct(v), w / 2, 1, 0L)
            }
        } finally {
            scaled.release()
            sensor.release()
            yuv.release()
        }
    }

    private fun subsample(plane: Mat, w: Int, h: Int): ByteArray {
        val half = Mat()
        Imgproc.resize(plane, half, Size(w / 2.0, h / 2.0), 0.0, 0.0, Imgproc.INTER_AREA)
        return ByteArray((w / 2) * (h / 2)).also { half.get(0, 0, it) }.also { half.release() }
    }

    /** first0, second0, first1, second1, ..., firstN; the trailing byte is cut as camera buffers do. */
    private fun interleave(first: ByteArray, second: ByteArray): ByteArray {
        val out = ByteArray(first.size * 2 - 1)
        for (i in first.indices) {
            out[2 * i] = first[i]
            if (2 * i + 1 < out.size) out[2 * i + 1] = second[i]
        }
        return out
    }

    private fun direct(bytes: ByteArray): ByteBuffer = ByteBuffer.allocateDirect(bytes.size).put(bytes).apply { rewind() }
}
