package com.clearcapture.clearcapture.quality

import com.clearcapture.clearcapture.domain.model.FrameData
import com.clearcapture.clearcapture.domain.model.GuideRegion
import com.clearcapture.clearcapture.opencv.FrameMats
import com.clearcapture.clearcapture.opencv.MatPool
import com.clearcapture.clearcapture.opencv.NormalizedRect
import com.clearcapture.clearcapture.opencv.YuvPlanes

/** Unpacks domain types into the raw arguments of `:core:opencv`, which has no domain dependency (D5). */
internal object FrameMatsAdapter {
    /** Lazy mats over [frame]'s planes; valid only while [frame] is. */
    fun live(frame: FrameData, guide: GuideRegion?, pool: MatPool): FrameMats = FrameMats.fromYuv(
        planes = YuvPlanes(
            width = frame.width,
            height = frame.height,
            y = frame.yPlane,
            yRowStride = frame.yRowStride,
            u = frame.uPlane,
            v = frame.vPlane,
            uvRowStride = frame.uvRowStride,
            uvPixelStride = frame.uvPixelStride,
        ),
        rotationDegrees = frame.rotationDegrees,
        guide = guide?.toNormalized(),
        pool = pool,
    )

    /** The guide as `:core:opencv` sees it. */
    fun GuideRegion.toNormalized(): NormalizedRect = NormalizedRect(left, top, right, bottom)
}
