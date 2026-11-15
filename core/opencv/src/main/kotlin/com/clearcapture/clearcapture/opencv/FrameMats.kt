package com.clearcapture.clearcapture.opencv

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer

/**
 * The images one analysis works on, in upright coordinates, built lazily so a mat exists only if a
 * validator asks for it. Live analysis therefore never builds [bgr] or [hsv] unless the glare check runs.
 *
 * Validators read these mats and must never write into them or keep references to them. [close] returns
 * every mat to the pool; after that the instance must not be used. The lazy properties are thread-safe,
 * so post-capture validators may run concurrently on one instance.
 */
public class FrameMats private constructor(
    private val pool: MatPool,
    /** Width of the upright image in pixels. */
    public val width: Int,
    /** Height of the upright image in pixels. */
    public val height: Int,
    guide: NormalizedRect?,
    private val source: Source,
    /** Post-capture only: a full-resolution grey crop centred on the guide region, upright. */
    public val sharpCrop: Mat?,
) : AutoCloseable {

    /** True when the profile defines a guide; [guideRect] is then the guide, otherwise the whole frame. */
    public val hasGuide: Boolean = guide != null

    /** The guide region in pixels of the upright image, or the whole image when there is no guide. */
    public val guideRect: Rect = guide?.toPixels(width, height) ?: Rect(0, 0, width, height)

    private val grayLazy = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        checkOpen()
        source.gray(pool)
    }
    private val bgrLazy = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        checkOpen()
        source.bgr(pool)
    }
    private val hsvLazy = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        checkOpen()
        pool.produce { Imgproc.cvtColor(bgr, it, Imgproc.COLOR_BGR2HSV) }
    }

    @Volatile
    private var closed = false

    /** 8-bit single-channel luminance. */
    public val gray: Mat by grayLazy

    /** 8-bit BGR. Built from the U and V planes for live frames. */
    public val bgr: Mat by bgrLazy

    /** 8-bit HSV (OpenCV ranges: H 0..179, S and V 0..255). */
    public val hsv: Mat by hsvLazy

    /** Whether [bgr] has been built; lets tests verify the live path stays on the Y plane. */
    public val isBgrBuilt: Boolean get() = bgrLazy.isInitialized() && source.bgrIsDerived

    /** Whether [hsv] has been built. */
    public val isHsvBuilt: Boolean get() = hsvLazy.isInitialized()

    override fun close() {
        synchronized(this) {
            if (closed) return
            closed = true
        }
        if (hsvLazy.isInitialized()) pool.recycle(hsvLazy.value)
        if (grayLazy.isInitialized()) source.releaseGray(pool, grayLazy.value)
        if (bgrLazy.isInitialized()) source.releaseBgr(pool, bgrLazy.value)
        source.close(pool)
        sharpCrop?.let(pool::discard)
    }

    private fun checkOpen() {
        check(!closed) { "FrameMats used after close" }
    }

    /** Where the pixels come from. */
    private interface Source {
        val bgrIsDerived: Boolean
        fun gray(pool: MatPool): Mat
        fun bgr(pool: MatPool): Mat
        fun releaseGray(pool: MatPool, mat: Mat) = pool.recycle(mat)
        fun releaseBgr(pool: MatPool, mat: Mat) = pool.recycle(mat)
        fun close(pool: MatPool) = Unit
    }

    /** A YUV_420_888 frame; the buffers are only read while the frame is open. */
    private class YuvSource(
        private val plane: YuvPlanes,
        private val rotateCode: Int?,
    ) : Source {
        override val bgrIsDerived: Boolean = true

        override fun gray(pool: MatPool): Mat = pool.scoped {
            val y = own(wrap(plane.y, plane.height, plane.width, plane.yRowStride))
            pool.produce { out -> upright(y, out) }
        }

        override fun bgr(pool: MatPool): Mat = pool.scoped {
            val u = plane.u
            val v = plane.v
            check(u != null && v != null) { "Colour planes are missing" }
            val y = own(wrap(plane.y, plane.height, plane.width, plane.yRowStride))
            val fullSize = Size(plane.width.toDouble(), plane.height.toDouble())
            val uFull = mat().also { Imgproc.resize(chroma(u, this), it, fullSize, 0.0, 0.0, Imgproc.INTER_LINEAR) }
            val vFull = mat().also { Imgproc.resize(chroma(v, this), it, fullSize, 0.0, 0.0, Imgproc.INTER_LINEAR) }
            // Camera YUV_420_888 is full-range JFIF YCbCr: U is Cb and V is Cr.
            val yCrCb = mat().also { Core.merge(listOf(y, vFull, uFull), it) }
            val sensorBgr = mat().also { Imgproc.cvtColor(yCrCb, it, Imgproc.COLOR_YCrCb2BGR) }
            pool.produce { out -> upright(sensorBgr, out) }
        }

        /** One subsampled chroma plane as a (height/2) × (width/2) single-channel mat, owned by [scope]. */
        private fun chroma(buffer: ByteBuffer, scope: MatScope): Mat {
            val rows = (plane.height + 1) / 2
            val cols = (plane.width + 1) / 2
            if (plane.uvPixelStride == 1) {
                return scope.own(wrap(buffer, rows, cols, plane.uvRowStride))
            }
            // Interleaved samples: copy the readable bytes of each row into a dense mat, then keep every
            // pixelStride-th byte. Reading only (cols - 1) * stride + 1 bytes per row stays inside the buffer.
            val readable = (cols - 1) * plane.uvPixelStride + 1
            val rowBytes = scope.own(wrap(buffer, rows, readable, plane.uvRowStride))
            val dense = scope.mat()
            dense.create(rows, cols * plane.uvPixelStride, CvType.CV_8UC1)
            rowBytes.copyTo(scope.roi(dense, Rect(0, 0, readable, rows)))
            val samples = scope.own(dense.reshape(plane.uvPixelStride))
            return scope.mat().also { Core.extractChannel(samples, it, 0) }
        }

        private fun upright(sensor: Mat, out: Mat) {
            if (rotateCode == null) sensor.copyTo(out) else Core.rotate(sensor, out, rotateCode)
        }
    }

    /** A decoded photograph, already upright; the BGR mat is owned by the frame. */
    private class BgrSource(private val image: Mat) : Source {
        override val bgrIsDerived: Boolean = false

        override fun gray(pool: MatPool): Mat = pool.produce { Imgproc.cvtColor(image, it, Imgproc.COLOR_BGR2GRAY) }

        override fun bgr(pool: MatPool): Mat = image

        override fun releaseBgr(pool: MatPool, mat: Mat) = Unit

        override fun close(pool: MatPool) = pool.discard(image)
    }

    /** Factories. */
    public companion object {
        /**
         * A live frame from YUV_420_888 planes. The buffers must stay valid until [close]; nothing is copied
         * until a mat is requested. [rotationDegrees] is the clockwise rotation that makes the frame upright.
         */
        public fun fromYuv(planes: YuvPlanes, rotationDegrees: Int, guide: NormalizedRect?, pool: MatPool): FrameMats {
            val rotateCode = ExifOrientation.rotateCode(rotationDegrees)
            val swapped = rotateCode == Core.ROTATE_90_CLOCKWISE || rotateCode == Core.ROTATE_90_COUNTERCLOCKWISE
            val width = if (swapped) planes.height else planes.width
            val height = if (swapped) planes.width else planes.height
            return FrameMats(pool, width, height, guide, YuvSource(planes, rotateCode), sharpCrop = null)
        }

        /**
         * A decoded photograph. Takes ownership of [decoded]'s mats and counts them in [pool] until [close].
         */
        public fun fromDecoded(decoded: DecodedImage, guide: NormalizedRect?, pool: MatPool): FrameMats {
            val image = pool.adopt(decoded.analysisBgr)
            val crop = decoded.sharpCrop?.let(pool::adopt)
            return FrameMats(pool, image.cols(), image.rows(), guide, BgrSource(image), crop)
        }

        /**
         * An 8-bit [rows] × [cols] view of [buffer] with [step] bytes per row. Direct buffers (what the camera
         * delivers) are wrapped without copying; heap buffers are copied into a mat that owns its memory.
         */
        private fun wrap(buffer: ByteBuffer, rows: Int, cols: Int, step: Int): Mat {
            val start = buffer.duplicate().apply { rewind() }
            val needed = (rows - 1) * step + cols
            require(start.remaining() >= needed) { "Plane buffer is smaller than its dimensions and stride require" }
            if (start.isDirect) return Mat(rows, cols, CvType.CV_8UC1, start.slice(), step.toLong())
            val bytes = ByteArray(needed).also { start.get(it) }
            val padded = Mat(rows, step, CvType.CV_8UC1)
            padded.put(0, 0, bytes)
            return padded.submat(0, rows, 0, cols).also { padded.release() }
        }
    }
}

/**
 * The planes of a YUV_420_888 image, as delivered by the camera, without Android types. Only valid
 * during the call it is passed to.
 */
public class YuvPlanes(
    /** Width in pixels, in sensor orientation. */
    public val width: Int,
    /** Height in pixels, in sensor orientation. */
    public val height: Int,
    /** Luminance plane. */
    public val y: ByteBuffer,
    /** Bytes between the starts of two rows of [y]. */
    public val yRowStride: Int,
    /** Cb plane, or `null` when colour is not available. */
    public val u: ByteBuffer?,
    /** Cr plane, or `null` when colour is not available. */
    public val v: ByteBuffer?,
    /** Bytes between the starts of two rows of [u] and [v]. */
    public val uvRowStride: Int,
    /** Bytes between two samples of one row of [u] and [v] (1 planar, 2 interleaved). */
    public val uvPixelStride: Int,
)
