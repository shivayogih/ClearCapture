package com.clearcapture.clearcapture.opencv

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import java.io.File

/** [JpegHeaderReader], [ExifOrientation] and [JpegDecoder] on JPEGs encoded in the test. */
class JpegTest {
    @get:Rule
    val temp = TemporaryFolder()

    init {
        DesktopNatives.load()
    }

    /** An asymmetric test image: a white block in the top-left corner on grey. */
    private fun marker(width: Int, height: Int): Mat = Mat(height, width, CvType.CV_8UC3, Scalar(90.0, 90.0, 90.0)).also {
        Imgproc.rectangle(it, Point(0.0, 0.0), Point(width * 0.25, height * 0.2), Scalar(250.0, 250.0, 250.0), -1)
    }

    private fun write(name: String, bytes: ByteArray): File = temp.newFile(name).apply { writeBytes(bytes) }

    @Test
    fun `reads dimensions, orientation and completeness`() {
        val jpeg = TestJpegs.encode(marker(640, 480))
        val plain = JpegHeaderReader.read(write("plain.jpg", jpeg))
        val rotated = JpegHeaderReader.read(write("rotated.jpg", TestJpegs.withOrientation(jpeg, 6)))
        val bigEndian = JpegHeaderReader.read(write("mm.jpg", TestJpegs.withOrientation(jpeg, 8, bigEndian = true)))
        assertThat(plain).isEqualTo(JpegHeader(640, 480, 0, complete = true, fileBytes = jpeg.size.toLong()))
        assertThat(rotated?.orientation).isEqualTo(6)
        assertThat(rotated?.uprightWidth).isEqualTo(480)
        assertThat(rotated?.uprightHeight).isEqualTo(640)
        assertThat(bigEndian?.orientation).isEqualTo(8)
    }

    @Test
    fun `keeps an invalid orientation value so it can be reported`() {
        val jpeg = TestJpegs.withOrientation(TestJpegs.encode(marker(64, 48)), 9)
        assertThat(JpegHeaderReader.read(write("nine.jpg", jpeg))?.orientation).isEqualTo(9)
    }

    @Test
    fun `detects truncation, also when data follows the end-of-image marker`() {
        val jpeg = TestJpegs.encode(marker(640, 480))
        val truncated = JpegHeaderReader.read(write("cut.jpg", jpeg.copyOf(jpeg.size / 2)))
        val trailer = JpegHeaderReader.read(write("trailer.jpg", jpeg + ByteArray(5000) { 0x55 }))
        assertThat(truncated?.complete).isFalse()
        assertThat(truncated?.width).isEqualTo(640)
        assertThat(trailer?.complete).isTrue()
    }

    @Test
    fun `rejects files that are not JPEGs`() {
        assertThat(JpegHeaderReader.read(write("text.jpg", "hello".toByteArray()))).isNull()
        assertThat(JpegHeaderReader.read(write("empty.jpg", ByteArray(0)))).isNull()
        assertThat(JpegHeaderReader.read(File(temp.root, "missing.jpg"))).isNull()
        assertThat(JpegHeaderReader.read(write("header-only.jpg", byteArrayOf(0xFF.toByte(), 0xD8.toByte())))).isNull()
    }

    @Test
    fun `every orientation maps the upright crop back onto the same stored pixels`() {
        val stored = marker(64, 48)
        val upright = Mat()
        val rect = Rect(5, 7, 20, 11)
        for (orientation in 1..8) {
            ExifOrientation.toUpright(stored, orientation, upright)
            val storedRect = ExifOrientation.uprightRectToStored(rect, orientation, upright.cols(), upright.rows())
            val fromStored = Mat()
            ExifOrientation.toUpright(stored.submat(storedRect), orientation, fromStored)
            val diff = Mat()
            Core.absdiff(upright.submat(rect), fromStored, diff)
            assertThat(Core.sumElems(diff).`val`.sum()).isEqualTo(0.0)
            assertThat(upright.cols()).isEqualTo(if (ExifOrientation.swapsAxes(orientation)) 48 else 64)
        }
    }

    @Test
    fun `decodes upright, normalised and with a full-resolution crop`() {
        val image = marker(4000, 3000)
        val jpeg = TestJpegs.withOrientation(TestJpegs.encode(image), 6)
        val file = write("big.jpg", jpeg)
        val header = checkNotNull(JpegHeaderReader.read(file))
        val decoder = JpegDecoder(JvmImageCodec())
        val decoded = checkNotNull(decoder.decode(file, header, NormalizedRect(0.1f, 0.1f, 0.9f, 0.9f)))
        // Rotated 90° clockwise: 3000 × 4000 upright, normalised to a 2000 px long side.
        assertThat(decoded.analysisBgr.cols()).isEqualTo(1500)
        assertThat(decoded.analysisBgr.rows()).isEqualTo(2000)
        val crop = checkNotNull(decoded.sharpCrop)
        assertThat(crop.cols()).isEqualTo(1024)
        assertThat(crop.rows()).isEqualTo(1024)
        // The white block (stored top-left) is upright top-right after a 90° clockwise turn.
        val topRight = decoded.analysisBgr.get(10, 1490)[0]
        val topLeft = decoded.analysisBgr.get(10, 10)[0]
        assertThat(topRight).isGreaterThan(200.0)
        assertThat(topLeft).isLessThan(120.0)
        assertThat(decoder.sampleSize(4000)).isEqualTo(2)
        assertThat(decoder.sampleSize(3264)).isEqualTo(1)
        assertThat(decoder.sampleSize(8000)).isEqualTo(4)
    }

    @Test
    fun `small images are not upscaled and the crop is clamped`() {
        val file = write("small.jpg", TestJpegs.encode(marker(800, 600)))
        val decoded = checkNotNull(JpegDecoder(JvmImageCodec()).decode(file, checkNotNull(JpegHeaderReader.read(file)), null))
        assertThat(decoded.analysisBgr.cols()).isEqualTo(800)
        assertThat(decoded.sharpCrop?.cols()).isEqualTo(800)
        assertThat(decoded.sharpCrop?.rows()).isEqualTo(600)
    }
}
