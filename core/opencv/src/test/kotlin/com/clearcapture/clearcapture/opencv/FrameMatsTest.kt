package com.clearcapture.clearcapture.opencv

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer

class FrameMatsTest {
    private val pool = MatPool()

    init {
        DesktopNatives.load()
    }

    /** A 64×48 upright test image: blue-ish background, a bright red block top-left. */
    private val upright: Mat = Mat(48, 64, CvType.CV_8UC3, Scalar(160.0, 90.0, 40.0)).also {
        Imgproc.rectangle(it, Point(0.0, 0.0), Point(15.0, 11.0), Scalar(30.0, 40.0, 230.0), -1)
    }

    /** [image] rotated so that turning it by [degrees] clockwise restores it, packed as YUV_420_888. */
    private fun planes(image: Mat, degrees: Int, interleaved: Boolean, padding: Int = 0, heap: Boolean = false): YuvPlanes {
        val sensor = Mat()
        when (degrees) {
            90 -> Core.rotate(image, sensor, Core.ROTATE_90_COUNTERCLOCKWISE)
            180 -> Core.rotate(image, sensor, Core.ROTATE_180)
            270 -> Core.rotate(image, sensor, Core.ROTATE_90_CLOCKWISE)
            else -> image.copyTo(sensor)
        }
        val w = sensor.cols()
        val h = sensor.rows()
        val ycc = Mat().also { Imgproc.cvtColor(sensor, it, Imgproc.COLOR_BGR2YCrCb) }
        val split = ArrayList<Mat>().also { Core.split(ycc, it) }
        fun bytes(mat: Mat) = ByteArray(mat.cols() * mat.rows()).also { mat.get(0, 0, it) }
        fun half(mat: Mat) = Mat().also { Imgproc.resize(mat, it, Size(w / 2.0, h / 2.0), 0.0, 0.0, Imgproc.INTER_AREA) }
        val stride = w + padding
        val yRows = bytes(split[0])
        val y = ByteArray(stride * (h - 1) + w)
        for (row in 0 until h) System.arraycopy(yRows, row * w, y, row * stride, w)
        val cr = bytes(half(split[1]))
        val cb = bytes(half(split[2]))
        fun buffer(array: ByteArray): ByteBuffer =
            if (heap) ByteBuffer.wrap(array) else ByteBuffer.allocateDirect(array.size).put(array).apply { rewind() }
        return if (interleaved) {
            val n = cb.size
            val u = ByteArray(2 * n - 1) { if (it % 2 == 0) cb[it / 2] else cr[it / 2] }
            val v = ByteArray(2 * n - 1) { if (it % 2 == 0) cr[it / 2] else cb[it / 2 + 1] }
            YuvPlanes(w, h, buffer(y), stride, buffer(u), buffer(v), w, 2)
        } else {
            YuvPlanes(w, h, buffer(y), stride, buffer(cb), buffer(cr), w / 2, 1)
        }
    }

    private fun meanAbsDiff(a: Mat, b: Mat): Double {
        val diff = Mat().also { Core.absdiff(a, b, it) }
        return Core.mean(diff).`val`.take(a.channels()).average()
    }

    @Test
    fun `gray and colour come back upright for every rotation and chroma layout`() {
        val expectedGray = Mat().also { Imgproc.cvtColor(upright, it, Imgproc.COLOR_BGR2GRAY) }
        for (degrees in listOf(0, 90, 180, 270)) {
            for (interleaved in listOf(false, true)) {
                FrameMats.fromYuv(planes(upright, degrees, interleaved, padding = 16), degrees, null, pool).use { frame ->
                    assertThat(frame.width).isEqualTo(64)
                    assertThat(frame.height).isEqualTo(48)
                    assertThat(meanAbsDiff(frame.gray, expectedGray)).isLessThan(1.0)
                    // Chroma is subsampled, so colour edges blur; the average error stays small.
                    assertThat(meanAbsDiff(frame.bgr, upright)).isLessThan(6.0)
                    assertThat(frame.hsv.channels()).isEqualTo(3)
                }
            }
        }
        assertThat(pool.outstanding).isEqualTo(0)
    }

    @Test
    fun `heap buffers are copied rather than wrapped`() {
        FrameMats.fromYuv(planes(upright, 0, interleaved = true, heap = true), 0, null, pool).use { frame ->
            assertThat(frame.gray.rows()).isEqualTo(48)
            assertThat(frame.bgr.cols()).isEqualTo(64)
        }
        assertThat(pool.outstanding).isEqualTo(0)
    }

    @Test
    fun `mats are built lazily and colour only on request`() {
        FrameMats.fromYuv(planes(upright, 0, interleaved = false), 0, null, pool).use { frame ->
            assertThat(pool.outstanding).isEqualTo(0)
            frame.gray
            assertThat(frame.isBgrBuilt).isFalse()
            assertThat(frame.isHsvBuilt).isFalse()
            frame.hsv
            assertThat(frame.isBgrBuilt).isTrue()
            assertThat(frame.isHsvBuilt).isTrue()
        }
        assertThat(pool.outstanding).isEqualTo(0)
    }

    @Test
    fun `the guide is in upright pixels and defaults to the whole frame`() {
        FrameMats.fromYuv(planes(upright, 90, false), 90, NormalizedRect(0.25f, 0.5f, 0.75f, 1f), pool).use { frame ->
            assertThat(frame.hasGuide).isTrue()
            assertThat(frame.guideRect).isEqualTo(Rect(16, 24, 32, 24))
        }
        FrameMats.fromYuv(planes(upright, 0, false), 0, null, pool).use { frame ->
            assertThat(frame.hasGuide).isFalse()
            assertThat(frame.guideRect).isEqualTo(Rect(0, 0, 64, 48))
        }
    }

    @Test
    fun `decoded photographs are owned and released by the frame`() {
        val decoded = DecodedImage(upright.clone(), Mat(10, 10, CvType.CV_8UC1))
        val frame = FrameMats.fromDecoded(decoded, null, pool)
        assertThat(pool.outstanding).isEqualTo(2)
        assertThat(frame.bgr).isSameInstanceAs(decoded.analysisBgr)
        frame.gray
        frame.close()
        frame.close()
        assertThat(pool.outstanding).isEqualTo(0)
        assertThat(decoded.analysisBgr.empty()).isTrue()
        assertThrows(IllegalStateException::class.java) { frame.hsv }
    }

    @Test
    fun `a plane buffer smaller than its stride requires is rejected`() {
        val planes = YuvPlanes(64, 48, ByteBuffer.allocateDirect(100), 64, null, null, 32, 1)
        FrameMats.fromYuv(planes, 0, null, pool).use { frame ->
            assertThrows(IllegalArgumentException::class.java) { frame.gray }
        }
        assertThat(pool.outstanding).isEqualTo(0)
    }
}
