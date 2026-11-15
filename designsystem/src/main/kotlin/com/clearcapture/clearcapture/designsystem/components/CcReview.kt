package com.clearcapture.clearcapture.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.clearcapture.clearcapture.designsystem.R
import com.clearcapture.clearcapture.designsystem.icons.CcIcons
import com.clearcapture.clearcapture.designsystem.theme.CcTheme

/**
 * A card for choosing what is being photographed. The whole card is one target, at least 88 dp
 * high; a selected card has the primary container colour, a border and a check mark.
 *
 * @param title Name of the profile.
 * @param description One line on when to use it.
 * @param selected Whether this profile is the chosen one.
 * @param onClick Chooses this profile.
 * @param modifier Layout of the card.
 * @param icon Optional picture of the subject.
 */
@Composable
public fun CcProfileCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    CcProfileCardContent(title, description, selected, onClick, modifier, icon, forcePressed = false)
}

/** [CcProfileCard] with the pressed look forced, for previews and screenshots. */
@Composable
internal fun CcProfileCardContent(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    icon: ImageVector?,
    forcePressed: Boolean,
) {
    val colors = CcTheme.colors
    val spacing = CcTheme.spacing
    val shape = CcTheme.shapes.large
    val container = if (selected) colors.primaryContainer else colors.surfaceVariant
    val titleColor = if (selected) colors.onPrimaryContainer else colors.onSurface
    val bodyColor = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val border = if (selected) {
        Modifier.border(CcTheme.sizes.selectedBorder, colors.primary, shape)
    } else {
        Modifier.border(CcTheme.sizes.border, colors.outline, shape)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = CcTheme.sizes.profileCardMinHeight)
            .clip(shape)
            .background(container, shape)
            .then(border)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = ripple(),
                role = Role.RadioButton,
                onClick = onClick,
            ),
    ) {
        if (pressed || forcePressed) {
            Box(modifier = Modifier.matchParentSize().background(titleColor.copy(alpha = PRESSED_OVERLAY_ALPHA)))
        }
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            if (icon != null) CcIconImage(icon = icon, tint = titleColor, size = CcTheme.sizes.iconInBanner)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(text = title, style = CcTheme.type.bodyStrong, color = titleColor)
                Text(text = description, style = CcTheme.type.body, color = bodyColor)
            }
            if (selected) CcIconImage(icon = CcIcons.CheckCircle, tint = titleColor, size = CcTheme.sizes.iconInControl)
        }
    }
}

/**
 * The overall result on the review screen: icon and word on the status colour. It is announced
 * when it appears or changes.
 *
 * @param verdict Pass, warning, fail, or still checking.
 * @param modifier Layout of the badge.
 */
@Composable
public fun CcVerdictBadge(verdict: CcVerdict, modifier: Modifier = Modifier) {
    val colors = CcTheme.colors
    val (container, content) = when (verdict) {
        CcVerdict.Pass -> colors.success to colors.onSuccess
        CcVerdict.Warning -> colors.warning to colors.onWarning
        CcVerdict.Fail -> colors.error to colors.onError
        CcVerdict.Loading -> colors.info to colors.onInfo
    }
    val word = when (verdict) {
        CcVerdict.Pass -> stringResource(R.string.cc_status_passed)
        CcVerdict.Warning -> stringResource(R.string.cc_status_check)
        CcVerdict.Fail -> stringResource(R.string.cc_status_retake)
        CcVerdict.Loading -> stringResource(R.string.cc_verdict_loading)
    }
    val iconSize = CcTheme.sizes.iconInControl
    Row(
        modifier = modifier
            .heightIn(min = CcTheme.sizes.minTouchTarget)
            .background(container, CcTheme.shapes.pill)
            .padding(horizontal = CcTheme.spacing.xl, vertical = CcTheme.spacing.sm)
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                heading()
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CcTheme.spacing.sm),
    ) {
        when (verdict) {
            CcVerdict.Pass -> CcIconImage(CcIcons.CheckCircle, content, iconSize)
            CcVerdict.Warning -> CcIconImage(CcIcons.Warning, content, iconSize)
            CcVerdict.Fail -> CcIconImage(CcIcons.CrossCircle, content, iconSize)
            CcVerdict.Loading -> CcProgressRing(content, iconSize)
        }
        Text(text = word, style = CcTheme.type.title, color = content)
    }
}

/**
 * One quality check on the review screen: name, status word, reason and advice, on the status
 * container colour with the status icon. Screen readers hear it as one sentence.
 *
 * @param name Name of the check, such as "Sharpness".
 * @param status Pass, warning or fail.
 * @param modifier Layout of the row.
 * @param reason What was found, in plain words.
 * @param advice What to do about it.
 */
@Composable
public fun CcCheckRow(
    name: String,
    status: CcCheckStatus,
    modifier: Modifier = Modifier,
    reason: String? = null,
    advice: String? = null,
) {
    val colors = CcTheme.colors
    val spacing = CcTheme.spacing
    val visual = when (status) {
        CcCheckStatus.Pass -> CheckVisual(
            colors.successContainer, colors.onSuccessContainer, colors.success, CcIcons.CheckCircle,
            stringResource(R.string.cc_status_passed),
        )
        CcCheckStatus.Warning -> CheckVisual(
            colors.warningContainer, colors.onWarningContainer, colors.warning, CcIcons.Warning,
            stringResource(R.string.cc_status_check),
        )
        CcCheckStatus.Fail -> CheckVisual(
            colors.errorContainer, colors.onErrorContainer, colors.error, CcIcons.CrossCircle,
            stringResource(R.string.cc_status_retake),
        )
    }
    val sentence = listOfNotNull(stringResource(R.string.cc_check_sentence, name, visual.word), reason, advice)
        .joinToString(" ")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(visual.container, CcTheme.shapes.medium)
            .padding(horizontal = spacing.lg, vertical = spacing.md)
            .semantics(mergeDescendants = true) { contentDescription = sentence },
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        CcIconImage(icon = visual.icon, tint = visual.accent, size = CcTheme.sizes.iconInControl)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(text = name, style = CcTheme.type.bodyStrong, color = visual.content)
            Text(text = visual.word, style = CcTheme.type.label, color = visual.content)
            if (reason != null) Text(text = reason, style = CcTheme.type.body, color = visual.content)
            if (advice != null) Text(text = advice, style = CcTheme.type.body, color = visual.content)
        }
    }
}

private class CheckVisual(
    val container: Color,
    val content: Color,
    val accent: Color,
    val icon: ImageVector,
    val word: String,
)
