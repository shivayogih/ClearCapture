package com.clearcapture.clearcapture.quality.fixtures

import com.clearcapture.clearcapture.quality.testing.DesktopOpenCv
import com.clearcapture.clearcapture.quality.testing.Fixtures
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.File
import java.util.Random
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Generates the synthetic fixture images (decision D22: Kotlin on the JVM, no Python).
 *
 * Every scene is the same plausible set-up: a shipping container (ribbed side wall, door panels with
 * stiffeners, locking bars, stencilled marks, rust spots) standing on asphalt under a hazy sky, both with
 * low-contrast texture. Each bad fixture changes exactly one property of the sharp scene. Output is
 * deterministic (fixed seeds). Run with `.\gradlew.bat :core:quality:generateFixtures`, or call [main].
 */
object FixtureGenerator {
    const val WIDTH = 2000
    const val HEIGHT = 1500
    private const val JPEG_QUALITY = 88
    private const val TRUNCATED_SHARE = 0.55

    /** Container position as fractions of the image. The container guide is 0.18..0.82 × 0.16..0.92. */
    data class Box(val left: Double, val top: Double, val right: Double, val bottom: Double)

    private val inGuide = Box(0.22, 0.24, 0.78, 0.86)

    /** Writes every fixture into [dir]. */
    fun generate(dir: File) {
        DesktopOpenCv.load()
        dir.mkdirs()
        val sharp = scene(WIDTH, HEIGHT, inGuide)
        write(dir, Fixtures.SHARP, sharp)
        write(dir, Fixtures.MOTION_BLUR, motionBlur(sharp, length = 61, degrees = 8.0))
        write(dir, Fixtures.DEFOCUS, Mat().also { Imgproc.GaussianBlur(sharp, it, Size(0.0, 0.0), DEFOCUS_SIGMA) })
        write(dir, Fixtures.DARK, Mat().also { sharp.convertTo(it, -1, DARK_GAIN, 0.0) })
        write(dir, Fixtures.OVEREXPOSED, Mat().also { sharp.convertTo(it, -1, BRIGHT_GAIN, BRIGHT_OFFSET) })
        write(dir, Fixtures.GLARE, glare(sharp))
        write(dir, Fixtures.CUT_OFF, scene(WIDTH, HEIGHT, Box(0.0, 0.24, 0.60, 0.86)))
        write(dir, Fixtures.TOO_SMALL, scene(WIDTH, HEIGHT, Box(0.42, 0.50, 0.58, 0.68)))
        write(dir, Fixtures.OBSTRUCTED, obstructed(sharp))
        write(dir, Fixtures.LOW_RESOLUTION, scene(1280, 960, inGuide))
        val bytes = File(dir, Fixtures.SHARP).readBytes()
        File(dir, Fixtures.TRUNCATED).writeBytes(bytes.copyOf((bytes.size * TRUNCATED_SHARE).roundToInt()))
    }

    /** The scene at [width] × [height] with the container at [box], 8-bit BGR. */
    fun scene(width: Int, height: Int, box: Box): Mat {
        val s = width / WIDTH.toDouble()
        val image = background(width, height)
        drawContainer(image, box, s, width, height)
        addTexture(image, width, height, s)
        return Mat().also { image.convertTo(it, CvType.CV_8UC3) }.also { image.release() }
    }

    private fun background(width: Int, height: Int): Mat {
        val column = Mat(height, 1, CvType.CV_32FC3)
        val horizon = HORIZON * height
        val blend = HORIZON_BLEND * height
        for (row in 0 until height) {
            val sky = lerp(SKY_TOP, SKY_BOTTOM, row / horizon)
            val ground = lerp(GROUND_FAR, GROUND_NEAR, (row - horizon) / (height - horizon))
            val t = ((row - (horizon - blend / 2)) / blend).coerceIn(0.0, 1.0)
            val smooth = t * t * (3 - 2 * t)
            val colour = lerp(sky, ground, smooth)
            column.put(row, 0, colour.map(Double::toFloat).toFloatArray())
        }
        return Mat().also { Core.repeat(column, 1, width, it) }.also { column.release() }
    }

    @Suppress("LongMethod") // One drawing routine per scene element would scatter the coordinates.
    private fun drawContainer(image: Mat, box: Box, s: Double, width: Int, height: Int) {
        val x0 = box.left * width
        val y0 = box.top * height
        val x1 = box.right * width
        val y1 = box.bottom * height
        val rail = 40 * s
        val doorStart = x0 + 0.55 * (x1 - x0)
        // Shadow on the ground, then the body.
        rect(image, x0 - 10 * s, y1, x1 + 30 * s, y1 + 26 * s, SHADOW)
        rect(image, x0, y0, x1, y1, BODY)
        // Side wall: vertical corrugation, one light and one dark face per rib, and horizontal weld seams.
        var x = x0
        while (x < doorStart) {
            rect(image, x, y0 + rail, minOf(x + 15 * s, doorStart), y1 - rail, RIB_LIGHT)
            rect(image, minOf(x + 15 * s, doorStart), y0 + rail, minOf(x + 22 * s, doorStart), y1 - rail, RIB_DARK)
            x += 40 * s
        }
        var y = y0 + rail + 150 * s
        while (y < y1 - rail) {
            rect(image, x0, y, doorStart, y + 5 * s, SEAM)
            y += 170 * s
        }
        // Doors: horizontal stiffeners, a centre gap, locking bars with handles.
        y = y0 + rail
        while (y < y1 - rail) {
            rect(image, doorStart, y, x1, minOf(y + 22 * s, y1 - rail), RIB_LIGHT)
            rect(image, doorStart, minOf(y + 22 * s, y1 - rail), x1, minOf(y + 30 * s, y1 - rail), RIB_DARK)
            y += 62 * s
        }
        val doorMid = (doorStart + x1) / 2
        rect(image, doorMid - 4 * s, y0 + rail, doorMid + 4 * s, y1 - rail, SEAM)
        listOf(0.18, 0.38, 0.62, 0.82).forEach { share ->
            val bar = doorStart + share * (x1 - doorStart)
            rect(image, bar - 7 * s, y0 + rail, bar + 7 * s, y1 - rail, STEEL)
            rect(image, bar - 7 * s, (y0 + y1) / 2 - 8 * s, bar + 60 * s, (y0 + y1) / 2 + 8 * s, STEEL)
        }
        // Top and bottom rails, corner posts.
        rect(image, x0, y0, x1, y0 + rail, RAIL)
        rect(image, x0, y1 - rail, x1, y1, RAIL)
        rect(image, x0, y0, x0 + 30 * s, y1, RAIL)
        rect(image, x1 - 30 * s, y0, x1, y1, RAIL)
        // Stencilled marks and rust.
        val font = Imgproc.FONT_HERSHEY_SIMPLEX
        val thickness = maxOf(2, (6 * s).roundToInt())
        Imgproc.putText(image, "CCLU 482915 3", Point(x0 + 60 * s, y0 + rail + 90 * s), font, 2.4 * s, PAINT, thickness)
        Imgproc.putText(image, "22G1", Point(x0 + 60 * s, y0 + rail + 170 * s), font, 2.0 * s, PAINT, thickness)
        Imgproc.putText(image, "MAX GROSS 30480 KG", Point(doorStart + 30 * s, y1 - rail - 40 * s), font, 1.1 * s, PAINT, thickness / 2)
        val random = Random(RUST_SEED)
        repeat(RUST_SPOTS) {
            val centre = Point(x0 + random.nextDouble() * (x1 - x0), y0 + random.nextDouble() * (y1 - y0))
            val axes = Size((5 + random.nextDouble() * 14) * s, (4 + random.nextDouble() * 10) * s)
            Imgproc.ellipse(image, centre, axes, random.nextDouble() * 180, 0.0, 360.0, RUST, -1)
        }
    }

    /** Low-contrast, smooth texture everywhere: visible to the obstruction check's deviation, not to Canny. */
    private fun addTexture(image: Mat, width: Int, height: Int, s: Double) {
        val random = Random(TEXTURE_SEED)
        listOf((28 * s) to 18.0, (6 * s) to 5.0).forEach { (cell, amplitude) ->
            val noise = smoothNoise(width, height, maxOf(2.0, cell), amplitude, random)
            val noise3 = Mat().also { Imgproc.cvtColor(noise, it, Imgproc.COLOR_GRAY2BGR) }
            Core.add(image, noise3, image)
            noise.release()
            noise3.release()
        }
    }

    private fun smoothNoise(width: Int, height: Int, cell: Double, amplitude: Double, random: Random): Mat {
        val cols = (width / cell).roundToInt() + 3
        val rows = (height / cell).roundToInt() + 3
        val grid = Mat(rows, cols, CvType.CV_32FC1)
        grid.put(0, 0, FloatArray(rows * cols) { ((random.nextDouble() * 2 - 1) * amplitude).toFloat() })
        return Mat().also { Imgproc.resize(grid, it, Size(width.toDouble(), height.toDouble()), 0.0, 0.0, Imgproc.INTER_CUBIC) }
            .also { grid.release() }
    }

    private fun motionBlur(source: Mat, length: Int, degrees: Double): Mat {
        val kernel = Mat.zeros(length, length, CvType.CV_32FC1)
        val c = length / 2.0
        val dx = cos(Math.toRadians(degrees)) * c
        val dy = sin(Math.toRadians(degrees)) * c
        Imgproc.line(kernel, Point(c - dx, c - dy), Point(c + dx, c + dy), Scalar(1.0), 1, Imgproc.LINE_AA)
        Core.divide(kernel, Scalar(Core.sumElems(kernel).`val`[0]), kernel)
        return Mat().also { Imgproc.filter2D(source, it, -1, kernel) }.also { kernel.release() }
    }

    /** A large, smooth, roughly white specular highlight on the side wall. */
    private fun glare(source: Mat): Mat {
        val alpha = Mat.zeros(source.rows(), source.cols(), CvType.CV_32FC1)
        Imgproc.ellipse(alpha, Point(0.42 * WIDTH, 0.47 * HEIGHT), Size(240.0, 170.0), -15.0, 0.0, 360.0, Scalar(1.0), -1)
        Imgproc.GaussianBlur(alpha, alpha, Size(41.0, 41.0), 0.0)
        return blend(source, alpha, GLARE_COLOUR)
    }

    /** An out-of-focus fingertip entering from the bottom-left corner. */
    private fun obstructed(source: Mat): Mat {
        val alpha = Mat.zeros(source.rows(), source.cols(), CvType.CV_32FC1)
        Imgproc.ellipse(alpha, Point(0.02 * WIDTH, 1.04 * HEIGHT), Size(0.74 * WIDTH, 0.82 * HEIGHT), -20.0, 0.0, 360.0, Scalar(1.0), -1)
        Imgproc.GaussianBlur(alpha, alpha, Size(151.0, 151.0), 0.0)
        return blend(source, alpha, SKIN)
    }

    /** source × (1 − alpha) + colour × alpha, 8-bit BGR. */
    private fun blend(source: Mat, alpha: Mat, colour: Scalar): Mat {
        val base = Mat().also { source.convertTo(it, CvType.CV_32FC3) }
        val alpha3 = Mat().also { Imgproc.cvtColor(alpha, it, Imgproc.COLOR_GRAY2BGR) }
        // Mat.ones() would set only the first channel of a 3-channel mat.
        val ones = Mat(alpha3.size(), alpha3.type(), Scalar.all(1.0))
        val inverse = Mat().also { Core.subtract(ones, alpha3, it) }
        val fill = Mat(base.size(), base.type(), colour)
        Core.multiply(base, inverse, base)
        Core.multiply(fill, alpha3, fill)
        Core.add(base, fill, base)
        val out = Mat().also { base.convertTo(it, CvType.CV_8UC3) }
        listOf(base, alpha3, ones, inverse, fill, alpha).forEach(Mat::release)
        return out
    }

    private fun rect(image: Mat, x0: Double, y0: Double, x1: Double, y1: Double, colour: Scalar) {
        Imgproc.rectangle(image, Point(x0, y0), Point(x1, y1), colour, -1)
    }

    private fun lerp(a: DoubleArray, b: DoubleArray, t: Double): DoubleArray {
        val k = t.coerceIn(0.0, 1.0)
        return DoubleArray(a.size) { a[it] + (b[it] - a[it]) * k }
    }

    private fun write(dir: File, name: String, image: Mat) {
        val params = MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, JPEG_QUALITY)
        check(Imgcodecs.imwrite(File(dir, name).path, image, params)) { "Could not write $name" }
        params.release()
    }

    private const val HORIZON = 0.40
    private const val HORIZON_BLEND = 0.06
    private const val DEFOCUS_SIGMA = 6.0
    private const val DARK_GAIN = 0.22
    private const val BRIGHT_GAIN = 1.6
    private const val BRIGHT_OFFSET = 70.0
    private const val RUST_SPOTS = 40
    private const val RUST_SEED = 7L
    private const val TEXTURE_SEED = 42L

    private val SKY_TOP = doubleArrayOf(205.0, 182.0, 150.0)
    private val SKY_BOTTOM = doubleArrayOf(190.0, 178.0, 162.0)
    private val GROUND_FAR = doubleArrayOf(112.0, 116.0, 120.0)
    private val GROUND_NEAR = doubleArrayOf(92.0, 98.0, 104.0)
    private val BODY = Scalar(50.0, 70.0, 165.0)
    private val RIB_LIGHT = Scalar(78.0, 98.0, 198.0)
    private val RIB_DARK = Scalar(34.0, 46.0, 118.0)
    private val SEAM = Scalar(28.0, 36.0, 92.0)
    private val RAIL = Scalar(30.0, 40.0, 100.0)
    private val STEEL = Scalar(150.0, 150.0, 155.0)
    private val PAINT = Scalar(215.0, 215.0, 215.0)
    private val RUST = Scalar(30.0, 45.0, 95.0)
    private val SHADOW = Scalar(58.0, 60.0, 64.0)
    private val GLARE_COLOUR = Scalar(255.0, 255.0, 255.0)
    private val SKIN = Scalar(120.0, 150.0, 200.0)
}

/** Writes the fixtures into the directory given as the first argument. */
fun main(args: Array<String>) {
    FixtureGenerator.generate(File(args.firstOrNull() ?: "src/test/resources/fixtures"))
}

/**
 * Gradle entry point for the generator; runs only through the `generateFixtures` task, which passes the
 * output directory. Excluded from the normal test tasks.
 */
class GenerateFixtures {
    @Test
    fun generate() {
        val dir = System.getProperty("clearcapture.fixturesDir")
        assumeTrue("Run through :core:quality:generateFixtures", dir != null)
        FixtureGenerator.generate(File(checkNotNull(dir)))
    }
}
