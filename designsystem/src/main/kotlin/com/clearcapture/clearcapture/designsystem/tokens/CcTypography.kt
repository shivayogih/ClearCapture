package com.clearcapture.clearcapture.designsystem.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Text styles of the SDK (DESIGN_SYSTEM.md section 3). Sizes are in sp and scale with the
 * user's font setting; the smallest text a user sees is [label] at 14 sp.
 *
 * @property hint The live hint on the camera screen: 24 / 30 SemiBold.
 * @property title Screen titles and the verdict: 20 / 26 SemiBold.
 * @property bodyStrong Check names and button labels: 16 / 24 Medium.
 * @property body Explanations and advice: 16 / 24 Regular.
 * @property label Chips and captions: 14 / 20 Medium.
 * @property mono Debug overlay only, never in the shipped flow: 12 / 16 monospace.
 */
@Immutable
public data class CcTypography(
    val hint: TextStyle,
    val title: TextStyle,
    val bodyStrong: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val mono: TextStyle,
)

/** The type scale with [fontFamily] (the system font when null). [CcTypography.mono] stays monospace. */
internal fun ccTypography(fontFamily: FontFamily?): CcTypography {
    val family = fontFamily ?: FontFamily.Default
    return CcTypography(
        hint = TextStyle(fontFamily = family, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
        title = TextStyle(fontFamily = family, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
        bodyStrong = TextStyle(
            fontFamily = family,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Medium,
        ),
        body = TextStyle(fontFamily = family, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
        label = TextStyle(fontFamily = family, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        mono = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Normal,
        ),
    )
}
