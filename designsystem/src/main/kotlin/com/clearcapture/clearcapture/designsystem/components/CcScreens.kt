package com.clearcapture.clearcapture.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.clearcapture.clearcapture.designsystem.R
import com.clearcapture.clearcapture.designsystem.icons.CcIcons
import com.clearcapture.clearcapture.designsystem.theme.CcTheme

private val StatusScreenIconSize = 48.dp

/**
 * Explains one permission before it is asked for: what it is, why in plain words, its state,
 * and one action. The action is "Allow" until it is denied permanently, then "Open settings";
 * a granted permission has no action.
 *
 * @param title Name of the permission, such as "Camera".
 * @param reason Why the SDK needs it, in one sentence.
 * @param state Not asked, granted, denied or permanently denied.
 * @param onAction Asks for the permission, or opens the settings when permanently denied.
 * @param modifier Layout of the card.
 * @param icon Optional picture of the permission.
 */
@Composable
public fun CcPermissionCard(
    title: String,
    reason: String,
    state: CcPermissionState,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val colors = CcTheme.colors
    val spacing = CcTheme.spacing
    val sizes = CcTheme.sizes
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceVariant, CcTheme.shapes.large)
            .padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            if (icon != null) CcIconImage(icon = icon, tint = colors.onSurface, size = sizes.iconInControl)
            Text(
                text = title,
                style = CcTheme.type.bodyStrong,
                color = colors.onSurface,
                modifier = Modifier.semantics { heading() },
            )
        }
        Text(text = reason, style = CcTheme.type.body, color = colors.onSurfaceVariant)
        val status = when (state) {
            CcPermissionState.Default -> null
            CcPermissionState.Granted ->
                Triple(CcIcons.CheckCircle, colors.success, stringResource(R.string.cc_permission_granted))
            CcPermissionState.Denied ->
                Triple(CcIcons.Warning, colors.warning, stringResource(R.string.cc_permission_denied))
            CcPermissionState.PermanentlyDenied ->
                Triple(CcIcons.CrossCircle, colors.error, stringResource(R.string.cc_permission_permanently_denied))
        }
        if (status != null) {
            Row(
                modifier = Modifier.semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                CcIconImage(icon = status.first, tint = status.second, size = sizes.iconInControl)
                Text(text = status.third, style = CcTheme.type.label, color = colors.onSurface)
            }
        }
        when (state) {
            CcPermissionState.Default, CcPermissionState.Denied ->
                CcPrimaryButton(text = stringResource(R.string.cc_permission_allow), onClick = onAction)
            CcPermissionState.PermanentlyDenied ->
                CcPrimaryButton(text = stringResource(R.string.cc_open_settings), onClick = onAction)
            CcPermissionState.Granted -> Unit
        }
    }
}

/**
 * A full-screen state such as "camera unavailable" or "storage full": an icon, a title, one
 * sentence and one action. The content scrolls, so nothing is cut off at large font sizes.
 *
 * @param kind Information or error; sets the icon and its colour.
 * @param title Short title.
 * @param message One sentence saying what is wrong and what to do next.
 * @param actionLabel Label of the single action.
 * @param onAction The action.
 * @param modifier Layout of the screen.
 */
@Composable
public fun CcStatusScreen(
    kind: CcStatusKind,
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CcTheme.colors
    val spacing = CcTheme.spacing
    val (icon, tint) = when (kind) {
        CcStatusKind.Info -> CcIcons.Info to colors.info
        CcStatusKind.Error -> CcIcons.CrossCircle to colors.error
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(colors.surface)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = spacing.screenEdge, vertical = spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(spacing.lg, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CcIconImage(icon = icon, tint = tint, size = StatusScreenIconSize)
            Text(
                text = title,
                style = CcTheme.type.title,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = message,
                style = CcTheme.type.body,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            CcPrimaryButton(text = actionLabel, onClick = onAction, modifier = Modifier.padding(top = spacing.sm))
        }
    }
}
