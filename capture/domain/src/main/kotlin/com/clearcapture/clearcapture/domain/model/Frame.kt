package com.clearcapture.clearcapture.domain.model

import java.nio.ByteBuffer

/** Normalised rectangle (0..1) in upright image coordinates. */
data class GuideRegion(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init {
        require(left in 0f..1f && right in 0f..1f && top in 0f..1f && bottom in 0f..1f) { "Guide must be within 0..1" }
        require(left < right && top < bottom) { "Guide must have a positive size" }
    }

    /** Width as a fraction of the image width. */
    val width: Float get() = right - left

    /** Height as a fraction of the image height. */
    val height: Float get() = bottom - top
}

/** Shape of the framing guide drawn over the preview. */
enum class GuideShape { CONTAINER, PORTRAIT, VEHICLE, NONE }

/**
 * A camera frame without Android types, so the domain stays pure Kotlin.
 * The buffers are only valid during the call they are passed to; never keep a reference.
 */
class FrameData(
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val yPlane: ByteBuffer,
    val yRowStride: Int,
    val uPlane: ByteBuffer?,
    val vPlane: ByteBuffer?,
    val uvRowStride: Int,
    val uvPixelStride: Int,
    val timestampNanos: Long,
)

/** Facts about a saved image, read from the file and its EXIF. */
data class ImageInfo(
    val width: Int,
    val height: Int,
    val fileBytes: Long,
    /** EXIF orientation value (1..8), or 0 when the tag is absent. */
    val orientation: Int,
    val captureTimeEpochMillis: Long?,
)
