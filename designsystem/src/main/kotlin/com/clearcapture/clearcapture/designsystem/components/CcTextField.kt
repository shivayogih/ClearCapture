package com.clearcapture.clearcapture.designsystem.components

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import com.clearcapture.clearcapture.designsystem.R
import com.clearcapture.clearcapture.designsystem.theme.CcTheme
import com.clearcapture.clearcapture.designsystem.theme.primaryTextOnSurface

private const val MIN_LINES = 3

/**
 * A multi-line text field with a label above it and a character count below, used for the
 * override reason. A focused field has a thicker primary border; with [errorMessage] the border,
 * label and message use the error colour and the error is reported to screen readers.
 *
 * Input longer than [maxLength] is cut to [maxLength] before [onValueChange] is called.
 *
 * @param value The current text.
 * @param onValueChange Called with the new text.
 * @param label What to type, shown above the field and read with it.
 * @param maxLength Maximum number of characters.
 * @param modifier Layout of the field.
 * @param errorMessage What is wrong with the text; null when nothing is.
 * @param enabled Whether the text can be edited.
 */
@Composable
public fun CcTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    maxLength: Int,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    enabled: Boolean = true,
) {
    CcTextFieldContent(value, onValueChange, label, maxLength, modifier, errorMessage, enabled, forceFocused = false)
}

/** [CcTextField] with the focused look forced, for previews and screenshots. */
@Composable
internal fun CcTextFieldContent(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    maxLength: Int,
    modifier: Modifier,
    errorMessage: String?,
    enabled: Boolean,
    forceFocused: Boolean,
) {
    val colors = CcTheme.colors
    val spacing = CcTheme.spacing
    val sizes = CcTheme.sizes
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val isError = errorMessage != null
    val accent = when {
        isError -> colors.error
        focused || forceFocused -> colors.primaryTextOnSurface
        else -> colors.outline
    }
    val borderWidth = if (isError || focused || forceFocused) sizes.selectedBorder else sizes.border
    val countDescription = stringResource(R.string.cc_character_count_description, value.length, maxLength)
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(it.take(maxLength)) },
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .semantics { if (errorMessage != null) error(errorMessage) },
        textStyle = CcTheme.type.body.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        minLines = MIN_LINES,
        interactionSource = interaction,
        decorationBox = { innerTextField ->
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(
                    text = label,
                    style = CcTheme.type.label,
                    color = if (isError) colors.error else colors.onSurfaceVariant,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = sizes.minTouchTarget)
                        .border(borderWidth, accent, CcTheme.shapes.small)
                        .padding(horizontal = spacing.md, vertical = spacing.md),
                ) {
                    innerTextField()
                }
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Text(
                        text = errorMessage.orEmpty(),
                        style = CcTheme.type.label,
                        color = colors.error,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(R.string.cc_character_count, value.length, maxLength),
                        style = CcTheme.type.label,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.semantics { contentDescription = countDescription },
                    )
                }
            }
        },
    )
}

