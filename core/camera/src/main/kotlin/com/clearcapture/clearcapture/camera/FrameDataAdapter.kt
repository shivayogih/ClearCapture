package com.clearcapture.clearcapture.camera

import androidx.camera.core.ImageProxy
import com.clearcapture.clearcapture.domain.model.FrameData

/**
 * Wraps the planes of an [ImageProxy] as a domain [FrameData] without copying.
 * The result is valid only until the proxy is closed.
 */
internal object FrameDataAdapter {

    /** Wraps [image]. The caller must close [image] after use, on every path. */
    fun wrap(image: ImageProxy): FrameData {
        val planes = image.planes
        val y = planes[0]
        val u = planes.getOrNull(1)
        val v = planes.getOrNull(2)
        return FrameData(
            width = image.width,
            height = image.height,
            rotationDegrees = image.imageInfo.rotationDegrees,
            yPlane = y.buffer,
            yRowStride = y.rowStride,
            uPlane = u?.buffer,
            vPlane = v?.buffer,
            uvRowStride = u?.rowStride ?: 0,
            uvPixelStride = u?.pixelStride ?: 0,
            timestampNanos = image.imageInfo.timestamp,
        )
    }
}
