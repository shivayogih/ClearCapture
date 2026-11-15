package com.clearcapture.clearcapture.designsystem.tokens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.pow
import kotlin.math.roundToInt

/** This colour as an unsigned packed sRGB value, `0xAARRGGBB`. */
internal fun Color.toArgbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL

/** WCAG 2.x contrast arithmetic on packed sRGB colours (`0xAARRGGBB`). */
internal object Contrast {

    /** Minimum contrast for body text (WCAG AA). */
    const val TEXT_MIN: Double = 4.5

    /** Minimum contrast for icons, large text and other non-text graphics (WCAG AA). */
    const val GRAPHIC_MIN: Double = 3.0

    private const val CHANNEL_MAX = 255.0
    private const val LINEAR_THRESHOLD = 0.04045
    private const val LINEAR_DIVISOR = 12.92
    private const val GAMMA_OFFSET = 0.055
    private const val GAMMA_DIVISOR = 1.055
    private const val GAMMA = 2.4
    private const val RED_WEIGHT = 0.2126
    private const val GREEN_WEIGHT = 0.7152
    private const val BLUE_WEIGHT = 0.0722
    private const val FLARE = 0.05

    /** Contrast ratio between two opaque colours, from 1.0 to 21.0. Alpha is ignored. */
    fun ratio(first: Long, second: Long): Double {
        val a = relativeLuminance(first)
        val b = relativeLuminance(second)
        return (maxOf(a, b) + FLARE) / (minOf(a, b) + FLARE)
    }

    /** WCAG relative luminance of an sRGB colour. Alpha is ignored. */
    fun relativeLuminance(argb: Long): Double =
        RED_WEIGHT * linear(channel(argb, RED_SHIFT)) +
            GREEN_WEIGHT * linear(channel(argb, GREEN_SHIFT)) +
            BLUE_WEIGHT * linear(channel(argb, BLUE_SHIFT))

    /** Whether the colour is fully opaque. */
    fun isOpaque(argb: Long): Boolean = channel(argb, ALPHA_SHIFT) == CHANNEL_MASK

    /** [foreground] drawn over the opaque [background], using the foreground's alpha. Returns an opaque colour. */
    fun composite(foreground: Long, background: Long): Long {
        val alpha = channel(foreground, ALPHA_SHIFT) / CHANNEL_MAX
        fun mix(shift: Int): Long {
            val value = channel(foreground, shift) * alpha + channel(background, shift) * (1 - alpha)
            return value.roundToInt().toLong() shl shift
        }
        return (CHANNEL_MASK.toLong() shl ALPHA_SHIFT) or mix(RED_SHIFT) or mix(GREEN_SHIFT) or mix(BLUE_SHIFT)
    }

    /** Linear interpolation between two opaque colours; [fraction] 0 gives [start], 1 gives [stop]. */
    fun lerp(start: Long, stop: Long, fraction: Double): Long {
        fun mix(shift: Int): Long {
            val value = channel(start, shift) + (channel(stop, shift) - channel(start, shift)) * fraction
            return value.roundToInt().coerceIn(0, CHANNEL_MASK).toLong() shl shift
        }
        return (CHANNEL_MASK.toLong() shl ALPHA_SHIFT) or mix(RED_SHIFT) or mix(GREEN_SHIFT) or mix(BLUE_SHIFT)
    }

    private fun channel(argb: Long, shift: Int): Int = ((argb shr shift) and CHANNEL_MASK.toLong()).toInt()

    private fun linear(channel: Int): Double {
        val c = channel / CHANNEL_MAX
        return if (c <= LINEAR_THRESHOLD) c / LINEAR_DIVISOR else ((c + GAMMA_OFFSET) / GAMMA_DIVISOR).pow(GAMMA)
    }

    private const val CHANNEL_MASK = 0xFF
    private const val ALPHA_SHIFT = 24
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val BLUE_SHIFT = 0
}
