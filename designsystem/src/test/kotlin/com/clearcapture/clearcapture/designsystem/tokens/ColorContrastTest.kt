package com.clearcapture.clearcapture.designsystem.tokens

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * Computes the WCAG contrast of every colour pair the design system draws text or icons with, so a
 * token change cannot silently break readability (DESIGN_SYSTEM.md sections 2 and 8).
 */
class ColorContrastTest {

    private class Pair(val name: String, val foreground: Color, val background: Color, val minimum: Double)

    private fun text(name: String, fg: Color, bg: Color) = Pair(name, fg, bg, Contrast.TEXT_MIN)
    private fun graphic(name: String, fg: Color, bg: Color) = Pair(name, fg, bg, Contrast.GRAPHIC_MIN)

    /** Text pairs of the tables in DESIGN_SYSTEM.md 2.1 and 2.2, plus the pairs components use. */
    private fun palettePairs(c: CcColors): List<Pair> = listOf(
        text("onPrimary/primary", c.onPrimary, c.primary),
        text("onPrimaryContainer/primaryContainer", c.onPrimaryContainer, c.primaryContainer),
        text("onSurface/surface", c.onSurface, c.surface),
        text("onSurfaceVariant/surfaceVariant", c.onSurfaceVariant, c.surfaceVariant),
        text("onSuccess/success", c.onSuccess, c.success),
        text("onSuccessContainer/successContainer", c.onSuccessContainer, c.successContainer),
        text("onWarning/warning", c.onWarning, c.warning),
        text("onWarningContainer/warningContainer", c.onWarningContainer, c.warningContainer),
        text("onError/error", c.onError, c.error),
        text("onErrorContainer/errorContainer", c.onErrorContainer, c.errorContainer),
        text("onInfo/info", c.onInfo, c.info),
        // Used by components beyond the table rows.
        text("onSurface/surfaceVariant (card titles)", c.onSurface, c.surfaceVariant),
        text("onSurfaceVariant/surface (secondary text)", c.onSurfaceVariant, c.surface),
        text("primary/surface (secondary button label)", c.primary, c.surface),
        text("error/surface (text field error)", c.error, c.surface),
        graphic("outline/surface (borders)", c.outline, c.surface),
        graphic("primary/surfaceVariant (selected border)", c.primary, c.surfaceVariant),
        graphic("success/successContainer (check icon)", c.success, c.successContainer),
        graphic("warning/warningContainer (check icon)", c.warning, c.warningContainer),
        graphic("error/errorContainer (check icon)", c.error, c.errorContainer),
        graphic("success/surfaceVariant (permission icon)", c.success, c.surfaceVariant),
        graphic("warning/surfaceVariant (permission icon)", c.warning, c.surfaceVariant),
        graphic("error/surfaceVariant (permission icon)", c.error, c.surfaceVariant),
        graphic("info/surface (status screen icon)", c.info, c.surface),
        graphic("error/surface (status screen icon)", c.error, c.surface),
    )

    /** Camera overlay pairs, measured on the worst-case scrim: the scrim over a pure white scene. */
    private fun cameraPairs(camera: CcCameraColors): List<Pair> {
        val worstScrim = Color(Contrast.composite(camera.scrim.toArgbLong(), WHITE))
        return listOf(
            text("onScrim/scrim over white", camera.onScrim, worstScrim),
            graphic("guideNeutral/scrim over white", camera.guideNeutral, worstScrim),
            graphic("guideReady/scrim over white", camera.guideReady, worstScrim),
            graphic("guideWarning/scrim over white", camera.guideWarning, worstScrim),
            graphic("guideProblem/scrim over white", camera.guideProblem, worstScrim),
            text("guideOutline/onScrim (toggled icon button)", camera.guideOutline, camera.onScrim),
        )
    }

    private fun assertAllReadable(pairs: List<Pair>) {
        val failures = pairs.mapNotNull { pair ->
            val ratio = Contrast.ratio(pair.foreground.toArgbLong(), pair.background.toArgbLong())
            if (ratio < pair.minimum) "${pair.name}: %.2f:1 < %.1f:1".format(ratio, pair.minimum) else null
        }
        assertWithMessage("Colour pairs below the WCAG minimum").that(failures).isEmpty()
    }

    @Test
    fun `light palette pairs reach their minimum contrast`() = assertAllReadable(palettePairs(LightColors))

    @Test
    fun `dark palette pairs reach their minimum contrast`() = assertAllReadable(palettePairs(DarkColors))

    @Test
    fun `camera overlay pairs reach their minimum contrast on the worst-case scrim`() =
        assertAllReadable(cameraPairs(CameraColors))

    @Test
    fun `scrim over a white scene is 474747`() {
        assertThat(Contrast.composite(CameraColors.scrim.toArgbLong(), WHITE)).isEqualTo(0xFF474747L)
    }

    @Test
    fun `contrast arithmetic matches known values`() {
        assertThat(Contrast.ratio(0xFF000000L, 0xFFFFFFFFL)).isWithin(0.01).of(21.0)
        assertThat(Contrast.ratio(0xFFFFFFFFL, 0xFFFFFFFFL)).isWithin(0.0001).of(1.0)
        // The documented primary pair: 6.5:1.
        assertThat(Contrast.ratio(0xFF0B6B53L, 0xFFFFFFFFL)).isWithin(0.05).of(6.5)
    }

    @Test
    fun `a pair below the minimum is reported`() {
        val failing = listOf(text("grey on white", Color(0xFFAAAAAA), Color(0xFFFFFFFF)))
        val failures = failing.filter {
            Contrast.ratio(it.foreground.toArgbLong(), it.background.toArgbLong()) < it.minimum
        }
        assertThat(failures).hasSize(1)
    }

    private companion object {
        const val WHITE = 0xFFFFFFFFL
    }
}
