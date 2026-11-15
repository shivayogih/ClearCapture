package com.clearcapture.clearcapture.opencv

import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Rect
import kotlin.math.roundToInt

/** A rectangle in fractions (0..1) of the upright image. Mirrors the domain `GuideRegion` without depending on it. */
public data class NormalizedRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    /** The rectangle in pixels of a [width] × [height] image, clamped to the image and at least 1 × 1. */
    public fun toPixels(width: Int, height: Int): Rect {
        val x0 = (left * width).roundToInt().coerceIn(0, width - 1)
        val y0 = (top * height).roundToInt().coerceIn(0, height - 1)
        val x1 = (right * width).roundToInt().coerceIn(x0 + 1, width)
        val y1 = (bottom * height).roundToInt().coerceIn(y0 + 1, height)
        return Rect(x0, y0, x1 - x0, y1 - y0)
    }
}

/**
 * EXIF orientation (1..8) and the transform that makes a stored image upright.
 * Values outside 1..8, including 0 for "no tag", are treated as 1.
 */
public object ExifOrientation {
    /** True for orientations that swap width and height (5..8). */
    public fun swapsAxes(orientation: Int): Boolean = orientation in TRANSPOSE..ROTATE_270

    /** Writes [stored] made upright into [upright]. [stored] and [upright] must be different mats. */
    public fun toUpright(stored: Mat, orientation: Int, upright: Mat) {
        when (orientation) {
            FLIP_HORIZONTAL -> Core.flip(stored, upright, 1)
            ROTATE_180 -> Core.rotate(stored, upright, Core.ROTATE_180)
            FLIP_VERTICAL -> Core.flip(stored, upright, 0)
            TRANSPOSE -> Core.transpose(stored, upright)
            ROTATE_90 -> Core.rotate(stored, upright, Core.ROTATE_90_CLOCKWISE)
            TRANSVERSE -> {
                Core.transpose(stored, upright)
                Core.flip(upright, upright, -1)
            }
            ROTATE_270 -> Core.rotate(stored, upright, Core.ROTATE_90_COUNTERCLOCKWISE)
            else -> stored.copyTo(upright)
        }
    }

    /**
     * Maps [rect], given in the upright image of size [uprightWidth] × [uprightHeight], to the stored image.
     * The inverse of [toUpright] applied to the rectangle's corners.
     */
    public fun uprightRectToStored(rect: Rect, orientation: Int, uprightWidth: Int, uprightHeight: Int): Rect {
        val x0 = rect.x
        val y0 = rect.y
        val x1 = rect.x + rect.width
        val y1 = rect.y + rect.height
        val w = uprightWidth
        val h = uprightHeight
        return when (orientation) {
            FLIP_HORIZONTAL -> corners(w - x1, y0, w - x0, y1)
            ROTATE_180 -> corners(w - x1, h - y1, w - x0, h - y0)
            FLIP_VERTICAL -> corners(x0, h - y1, x1, h - y0)
            TRANSPOSE -> corners(y0, x0, y1, x1)
            ROTATE_90 -> corners(y0, w - x1, y1, w - x0)
            TRANSVERSE -> corners(h - y1, w - x1, h - y0, w - x0)
            ROTATE_270 -> corners(h - y1, x0, h - y0, x1)
            else -> corners(x0, y0, x1, y1)
        }
    }

    /** Clockwise rotation in degrees (0, 90, 180, 270) to OpenCV's rotate code, or `null` for none. */
    public fun rotateCode(rotationDegrees: Int): Int? = when (Math.floorMod(rotationDegrees, FULL_TURN)) {
        QUARTER_TURN -> Core.ROTATE_90_CLOCKWISE
        HALF_TURN -> Core.ROTATE_180
        THREE_QUARTER_TURN -> Core.ROTATE_90_COUNTERCLOCKWISE
        else -> null
    }

    private fun corners(x0: Int, y0: Int, x1: Int, y1: Int): Rect = Rect(x0, y0, x1 - x0, y1 - y0)

    private const val FLIP_HORIZONTAL = 2
    private const val ROTATE_180 = 3
    private const val FLIP_VERTICAL = 4
    private const val TRANSPOSE = 5
    private const val ROTATE_90 = 6
    private const val TRANSVERSE = 7
    private const val ROTATE_270 = 8
    private const val QUARTER_TURN = 90
    private const val HALF_TURN = 180
    private const val THREE_QUARTER_TURN = 270
    private const val FULL_TURN = 360
}
