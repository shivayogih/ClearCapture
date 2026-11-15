package com.clearcapture.clearcapture.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.font.FontFamily

/**
 * What a host may change about the SDK's look (DESIGN_SYSTEM.md section 2.5). This is the design
 * system's own input type; `:capture:api` maps the public `Appearance` to it.
 *
 * Status colours, camera overlay colours, touch-target sizes and text sizes cannot be changed.
 *
 * @property brandColor Replaces `primary`, as `0xAARRGGBB`. Must be opaque and set together with [onBrandColor].
 * @property onBrandColor Replaces `onPrimary`, as `0xAARRGGBB`. Must reach 4.5:1 against [brandColor].
 * @property darkMode Light, dark, or the system setting.
 * @property cornerStyle Rounded (default) or square corners.
 * @property fontFamily Font for all text; the system font when null.
 */
@Immutable
public data class CcAppearance(
    val brandColor: Long? = null,
    val onBrandColor: Long? = null,
    val darkMode: CcDarkMode = CcDarkMode.FOLLOW_SYSTEM,
    val cornerStyle: CcCornerStyle = CcCornerStyle.ROUNDED,
    val fontFamily: FontFamily? = null,
)

/** Which palette the SDK uses. */
public enum class CcDarkMode {
    /** Follow the system dark theme setting. */
    FOLLOW_SYSTEM,

    /** Always the light palette. */
    LIGHT,

    /** Always the dark palette. */
    DARK,
}

/** Corner style of cards, buttons, banners and chips. Round controls stay round. */
public enum class CcCornerStyle {
    /** Small 8, medium 12, large 16 and pill 28 dp corners. */
    ROUNDED,

    /** Square corners everywhere except round controls. */
    SQUARE,
}

/**
 * Why a host's brand colours were not used. The SDK then uses its default primary colours.
 *
 * @property brandColor The rejected brand colour, if one was given.
 * @property onBrandColor The rejected on-brand colour, if one was given.
 * @property reason What is wrong with the pair.
 * @property contrastRatio The measured contrast, when both colours were given and opaque.
 */
@Immutable
public data class CcBrandColorRejection(
    val brandColor: Long?,
    val onBrandColor: Long?,
    val reason: Reason,
    val contrastRatio: Double?,
) {
    /** Why the pair was rejected. */
    public enum class Reason {
        /** Only one of `brandColor` and `onBrandColor` was set. */
        INCOMPLETE_PAIR,

        /** One of the colours is not fully opaque, so its contrast cannot be guaranteed. */
        NOT_OPAQUE,

        /** The contrast between the two colours is below 4.5:1. */
        LOW_CONTRAST,
    }
}
