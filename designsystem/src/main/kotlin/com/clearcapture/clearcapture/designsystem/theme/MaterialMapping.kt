package com.clearcapture.clearcapture.designsystem.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.unit.dp
import com.clearcapture.clearcapture.designsystem.tokens.CcColors
import com.clearcapture.clearcapture.designsystem.tokens.CcShapes
import com.clearcapture.clearcapture.designsystem.tokens.CcTypography

/** Material 3 colour scheme for the wrapped Material components, built only from SDK tokens. */
internal fun CcColors.toMaterialColorScheme(): ColorScheme {
    val scheme = if (isDark) darkColorScheme() else lightColorScheme()
    return scheme.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = primary,
        onSecondary = onPrimary,
        secondaryContainer = primaryContainer,
        onSecondaryContainer = onPrimaryContainer,
        tertiary = info,
        onTertiary = onInfo,
        background = surface,
        onBackground = onSurface,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surfaceVariant,
        surfaceContainer = surfaceVariant,
        surfaceContainerHigh = surfaceVariant,
        surfaceContainerHighest = surfaceVariant,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        outline = outline,
        outlineVariant = outline,
    )
}

/** Material 3 type scale mapped from the SDK's six styles. */
internal fun CcTypography.toMaterialTypography(): Typography = Typography(
    headlineSmall = title,
    titleLarge = title,
    titleMedium = bodyStrong,
    titleSmall = label,
    bodyLarge = body,
    bodyMedium = body,
    bodySmall = label,
    labelLarge = bodyStrong,
    labelMedium = label,
    labelSmall = label,
)

/** Material 3 shapes mapped from the SDK's corner tokens. */
internal fun CcShapes.toMaterialShapes(): Shapes {
    fun Any.asCornerShape(): CornerBasedShape = this as? CornerBasedShape ?: RoundedCornerShape(0.dp)
    return Shapes(
        extraSmall = small.asCornerShape(),
        small = small.asCornerShape(),
        medium = medium.asCornerShape(),
        large = large.asCornerShape(),
        extraLarge = pill.asCornerShape(),
    )
}
