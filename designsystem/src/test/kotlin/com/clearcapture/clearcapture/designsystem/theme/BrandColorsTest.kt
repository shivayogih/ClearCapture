package com.clearcapture.clearcapture.designsystem.theme

import androidx.compose.ui.graphics.Color
import com.clearcapture.clearcapture.designsystem.tokens.Contrast
import com.clearcapture.clearcapture.designsystem.tokens.DarkColors
import com.clearcapture.clearcapture.designsystem.tokens.LightColors
import com.clearcapture.clearcapture.designsystem.tokens.toArgbLong
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BrandColorsTest {

    @Test
    fun `no brand pair keeps the default palette`() {
        val resolved = resolveColors(LightColors, null, null)
        assertThat(resolved.colors).isEqualTo(LightColors)
        assertThat(resolved.rejection).isNull()
    }

    @Test
    fun `a readable brand pair replaces primary and derives readable containers`() {
        listOf(LightColors, DarkColors).forEach { base ->
            BRANDS.forEach { (brand, onBrand) ->
                val resolved = resolveColors(base, brand, onBrand)
                assertThat(resolved.rejection).isNull()
                assertThat(resolved.colors.primary).isEqualTo(Color(brand))
                assertThat(resolved.colors.onPrimary).isEqualTo(Color(onBrand))
                val ratio = Contrast.ratio(
                    resolved.colors.onPrimaryContainer.toArgbLong(),
                    resolved.colors.primaryContainer.toArgbLong(),
                )
                assertThat(ratio).isAtLeast(Contrast.TEXT_MIN)
            }
        }
    }

    @Test
    fun `status and camera colours are never changed by a brand pair`() {
        val resolved = resolveColors(LightColors, 0xFF1F4FBFL, 0xFFFFFFFFL).colors
        assertThat(resolved.copy(
            primary = LightColors.primary,
            onPrimary = LightColors.onPrimary,
            primaryContainer = LightColors.primaryContainer,
            onPrimaryContainer = LightColors.onPrimaryContainer,
        )).isEqualTo(LightColors)
    }

    @Test
    fun `a pair below 4_5 to 1 is refused with its ratio and the default is used`() {
        val resolved = resolveColors(LightColors, 0xFFFFD54FL, 0xFFFFFFFFL)
        assertThat(resolved.colors).isEqualTo(LightColors)
        val rejection = requireNotNull(resolved.rejection)
        assertThat(rejection.reason).isEqualTo(CcBrandColorRejection.Reason.LOW_CONTRAST)
        assertThat(rejection.contrastRatio).isLessThan(Contrast.TEXT_MIN)
        assertThat(rejection.brandColor).isEqualTo(0xFFFFD54FL)
    }

    @Test
    fun `an incomplete pair is refused`() {
        val resolved = resolveColors(DarkColors, 0xFF1F4FBFL, null)
        assertThat(resolved.colors).isEqualTo(DarkColors)
        assertThat(resolved.rejection?.reason).isEqualTo(CcBrandColorRejection.Reason.INCOMPLETE_PAIR)
    }

    @Test
    fun `a translucent pair is refused`() {
        val resolved = resolveColors(LightColors, 0x801F4FBFL, 0xFFFFFFFFL)
        assertThat(resolved.rejection?.reason).isEqualTo(CcBrandColorRejection.Reason.NOT_OPAQUE)
    }

    @Test
    fun `primary text on the surface falls back to onSurface when the brand is not readable there`() {
        val darkBrandOnDark = resolveColors(DarkColors, 0xFF0B2F6BL, 0xFFFFFFFFL).colors
        assertThat(darkBrandOnDark.primaryTextOnSurface).isEqualTo(DarkColors.onSurface)
        assertThat(LightColors.primaryTextOnSurface).isEqualTo(LightColors.primary)
        assertThat(DarkColors.primaryTextOnSurface).isEqualTo(DarkColors.primary)
    }

    private companion object {
        val BRANDS = listOf(
            0xFF1F4FBFL to 0xFFFFFFFFL,
            0xFF6B2FA0L to 0xFFFFFFFFL,
            0xFFFFD54FL to 0xFF000000L,
            0xFF000000L to 0xFFFFFFFFL,
            0xFFFFFFFFL to 0xFF000000L,
            0xFFE53935L to 0xFF000000L,
        )
    }
}
