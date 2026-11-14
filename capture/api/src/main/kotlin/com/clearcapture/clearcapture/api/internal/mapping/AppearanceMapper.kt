package com.clearcapture.clearcapture.api.internal.mapping

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.clearcapture.clearcapture.api.Appearance
import com.clearcapture.clearcapture.api.CornerStyle
import com.clearcapture.clearcapture.api.DarkMode
import com.clearcapture.clearcapture.designsystem.theme.CcAppearance
import com.clearcapture.clearcapture.designsystem.theme.CcCornerStyle
import com.clearcapture.clearcapture.designsystem.theme.CcDarkMode

/** Maps the public [Appearance] to the design system's own type; `:designsystem` never sees the public API. */
internal object AppearanceMapper {
    fun map(appearance: Appearance): CcAppearance = CcAppearance(
        brandColor = appearance.brandColor,
        onBrandColor = appearance.onBrandColor,
        darkMode = when (appearance.darkMode) {
            DarkMode.FOLLOW_SYSTEM -> CcDarkMode.FOLLOW_SYSTEM
            DarkMode.LIGHT -> CcDarkMode.LIGHT
            DarkMode.DARK -> CcDarkMode.DARK
        },
        cornerStyle = when (appearance.cornerStyle) {
            CornerStyle.ROUNDED -> CcCornerStyle.ROUNDED
            CornerStyle.SQUARE -> CcCornerStyle.SQUARE
        },
        fontFamily = appearance.fontFamilyResId?.let { FontFamily(Font(it)) },
    )
}
