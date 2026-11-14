package com.clearcapture.clearcapture.designsystem.theme

import androidx.compose.ui.graphics.Color
import com.clearcapture.clearcapture.designsystem.tokens.CcColors
import com.clearcapture.clearcapture.designsystem.tokens.Contrast
import com.clearcapture.clearcapture.designsystem.tokens.toArgbLong

/** The palette to use and, when the host's brand pair was refused, why. */
internal data class ResolvedColors(val colors: CcColors, val rejection: CcBrandColorRejection?)

private const val OPAQUE_BLACK = 0xFF000000L
private const val OPAQUE_WHITE = 0xFFFFFFFFL
private const val LIGHT_CONTAINER_BRAND_SHARE = 0.22
private const val DARK_CONTAINER_BRAND_SHARE = 0.38
private const val LIGHT_ON_CONTAINER_DARKEN = 0.6
private const val DARK_ON_CONTAINER_LIGHTEN = 0.72

/**
 * Applies the host's brand pair to [base]. Without a brand pair [base] is returned unchanged.
 * A pair that is incomplete, translucent or below 4.5:1 is refused: [base] is returned with the
 * reason, so the caller can log it.
 */
internal fun resolveColors(base: CcColors, brandColor: Long?, onBrandColor: Long?): ResolvedColors {
    if (brandColor == null && onBrandColor == null) return ResolvedColors(base, null)
    if (brandColor == null || onBrandColor == null) {
        return refuse(base, brandColor, onBrandColor, CcBrandColorRejection.Reason.INCOMPLETE_PAIR, null)
    }
    val opaque = Contrast.isOpaque(brandColor) && Contrast.isOpaque(onBrandColor)
    val ratio = if (opaque) Contrast.ratio(brandColor, onBrandColor) else null
    val problem = when {
        ratio == null -> CcBrandColorRejection.Reason.NOT_OPAQUE
        ratio < Contrast.TEXT_MIN -> CcBrandColorRejection.Reason.LOW_CONTRAST
        else -> null
    }
    if (problem != null) return refuse(base, brandColor, onBrandColor, problem, ratio)
    val (container, onContainer) = deriveContainers(base, brandColor)
    val colors = base.copy(
        primary = Color(brandColor),
        onPrimary = Color(onBrandColor),
        primaryContainer = Color(container),
        onPrimaryContainer = Color(onContainer),
    )
    return ResolvedColors(colors, null)
}

/**
 * `primaryContainer` is the brand colour mixed into the surface; `onPrimaryContainer` is the brand
 * colour darkened (light) or lightened (dark), replaced by the best-contrast neutral if that pair
 * would stay below 4.5:1.
 */
internal fun deriveContainers(base: CcColors, brandColor: Long): Pair<Long, Long> {
    val surface = base.surface.toArgbLong()
    val container: Long
    val candidate: Long
    if (base.isDark) {
        container = Contrast.lerp(surface, brandColor, DARK_CONTAINER_BRAND_SHARE)
        candidate = Contrast.lerp(brandColor, OPAQUE_WHITE, DARK_ON_CONTAINER_LIGHTEN)
    } else {
        container = Contrast.lerp(surface, brandColor, LIGHT_CONTAINER_BRAND_SHARE)
        candidate = Contrast.lerp(brandColor, OPAQUE_BLACK, LIGHT_ON_CONTAINER_DARKEN)
    }
    if (Contrast.ratio(candidate, container) >= Contrast.TEXT_MIN) return container to candidate
    val onSurface = base.onSurface.toArgbLong()
    if (Contrast.ratio(onSurface, container) >= Contrast.TEXT_MIN) return container to onSurface
    val black = Contrast.ratio(OPAQUE_BLACK, container)
    val white = Contrast.ratio(OPAQUE_WHITE, container)
    return container to if (black >= white) OPAQUE_BLACK else OPAQUE_WHITE
}

private fun refuse(
    base: CcColors,
    brandColor: Long?,
    onBrandColor: Long?,
    reason: CcBrandColorRejection.Reason,
    ratio: Double?,
): ResolvedColors = ResolvedColors(base, CcBrandColorRejection(brandColor, onBrandColor, reason, ratio))

/**
 * The colour for primary-coloured text on [CcColors.surface]: [CcColors.primary] when it reaches
 * 4.5:1 there (always true for the default palettes), otherwise [CcColors.onSurface]. A brand
 * colour only has to contrast with its own `onBrandColor`, so it can be too dark or too light
 * to be used as text on the surface.
 */
internal val CcColors.primaryTextOnSurface: Color
    get() {
        val primaryArgb = primary.toArgbLong()
        val surfaceArgb = surface.toArgbLong()
        return if (Contrast.ratio(primaryArgb, surfaceArgb) >= Contrast.TEXT_MIN) primary else onSurface
    }
