package com.clearcapture.clearcapture.opencv

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.File

/** Loads the real Android OpenCV library from the AAR and runs it, including the Android JPEG decoder. */
@RunWith(AndroidJUnit4::class)
class OpenCvSmokeTest {

    @Test
    fun loadsTheAndroidLibraryAndRunsAMatOperation() = runTest {
        val initializer = OpenCvInitializer(Dispatchers.Default)
        assertThat(initializer.initialize()).isEqualTo(OpenCvStatus.Loaded)

        val pool = MatPool()
        val sum = pool.scoped {
            val image = own(Mat(48, 64, CvType.CV_8UC1, Scalar(3.0)))
            val doubled = mat().also { Core.add(image, image, it) }
            Core.sumElems(doubled).`val`[0]
        }
        assertThat(sum).isEqualTo(6.0 * 48 * 64)
        assertThat(pool.outstanding).isEqualTo(0)
    }

    @Test
    fun decodesAJpegWithBitmapFactoryAndTheRegionDecoder() = runTest {
        assertThat(OpenCvInitializer(Dispatchers.Default).initialize()).isEqualTo(OpenCvStatus.Loaded)
        val image = Mat(1500, 2000, CvType.CV_8UC3, Scalar(90.0, 90.0, 90.0))
        Imgproc.rectangle(image, Point(0.0, 0.0), Point(400.0, 300.0), Scalar(250.0, 250.0, 250.0), -1)
        val encoded = MatOfByte()
        assertThat(Imgcodecs.imencode(".jpg", image, encoded)).isTrue()
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val file = File(dir, "smoke.jpg").apply { writeBytes(encoded.toArray()) }

        val header = checkNotNull(JpegHeaderReader.read(file))
        val decoded = checkNotNull(JpegDecoder(AndroidImageCodec()).decode(file, header, NormalizedRect(0.2f, 0.2f, 0.8f, 0.8f)))
        assertThat(decoded.analysisBgr.cols()).isEqualTo(2000)
        assertThat(decoded.analysisBgr.rows()).isEqualTo(1500)
        assertThat(decoded.analysisBgr.get(10, 10)[0]).isGreaterThan(200.0)
        assertThat(decoded.sharpCrop?.cols()).isEqualTo(1024)

        listOf(image, encoded, decoded.analysisBgr).forEach(Mat::release)
        decoded.sharpCrop?.release()
        file.delete()
    }
}
