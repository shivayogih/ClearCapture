package com.clearcapture.clearcapture.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import com.clearcapture.clearcapture.designsystem.R
import com.clearcapture.clearcapture.designsystem.theme.CcTheme
import com.clearcapture.clearcapture.designsystem.theme.primaryTextOnSurface

/**
 * The main action of a screen: Accept, Continue, Grant. At least 56 dp high; full width on phones.
 * While [loading] it shows a progress ring, keeps its colour and cannot be pressed.
 *
 * @param text The label.
 * @param onClick Called on click.
 * @param modifier Layout of the button.
 * @param enabled Whether it can be pressed.
 * @param loading Whether the action is in progress.
 */
@Composable
public fun CcPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val colors = CcTheme.colors
    val loadingText = stringResource(R.string.cc_loading)
    val disabledContainer = if (loading) colors.primary else colors.onSurface.copy(alpha = DISABLED_CONTAINER_ALPHA)
    val disabledContent = if (loading) colors.onPrimary else colors.onSurface.copy(alpha = DISABLED_ALPHA)
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .then(buttonSize())
            .semantics { if (loading) stateDescription = loadingText },
        shape = CcTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
            disabledContainerColor = disabledContainer,
            disabledContentColor = disabledContent,
        ),
        contentPadding = buttonPadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CcTheme.spacing.sm),
        ) {
            if (loading) CcProgressRing(color = colors.onPrimary, size = CcTheme.sizes.iconInControl)
            Text(text = text, style = CcTheme.type.bodyStrong, textAlign = TextAlign.Center)
        }
    }
}

/**
 * A secondary action: Retake, Not now. The same size as [CcPrimaryButton].
 *
 * @param text The label.
 * @param onClick Called on click.
 * @param modifier Layout of the button.
 * @param enabled Whether it can be pressed.
 */
@Composable
public fun CcSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = CcTheme.colors
    val content = colors.primaryTextOnSurface
    val borderColor = if (enabled) content else colors.onSurface.copy(alpha = DISABLED_CONTAINER_ALPHA)
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.then(buttonSize()),
        shape = CcTheme.shapes.medium,
        border = BorderStroke(CcTheme.sizes.selectedBorder, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = colors.surface,
            contentColor = content,
            disabledContainerColor = colors.surface,
            disabledContentColor = colors.onSurface.copy(alpha = DISABLED_ALPHA),
        ),
        contentPadding = buttonPadding(),
    ) {
        Text(text = text, style = CcTheme.type.bodyStrong, textAlign = TextAlign.Center)
    }
}

/** Alpha of a disabled button's container (Material guidance). */
private const val DISABLED_CONTAINER_ALPHA = 0.12f

/** At least 56 dp high; full width on phones. */
@Composable
private fun buttonSize(): Modifier {
    val sized = Modifier.heightIn(min = CcTheme.sizes.buttonMinHeight)
    return if (CcTheme.isTablet) sized else sized.fillMaxWidth()
}

@Composable
private fun buttonPadding(): PaddingValues =
    PaddingValues(horizontal = CcTheme.spacing.xl, vertical = CcTheme.spacing.sm)
