package com.clearcapture.clearcapture.designsystem.components

import android.os.SystemClock
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import com.clearcapture.clearcapture.designsystem.R
import com.clearcapture.clearcapture.designsystem.a11y.AnnouncementRateLimiter
import com.clearcapture.clearcapture.designsystem.icons.CcIcons
import com.clearcapture.clearcapture.designsystem.theme.CcTheme
import kotlinx.coroutines.delay

/**
 * The background of anything drawn over the camera preview: black at 72 %. Text and icons never
 * sit directly on the preview.
 *
 * @param modifier Layout of the scrim.
 * @param shape Shape of the scrim; a full-bleed rectangle by default.
 * @param content What sits on the scrim.
 */
@Composable
public fun CcScrim(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier = modifier.background(CcTheme.colors.camera.scrim, shape), content = content)
}

/**
 * The single live instruction on the camera screen: a status icon and one short hint on a scrim.
 * The hint cross-fades when it changes. Screen readers hear it politely, at most once every 2 s;
 * a hint that changes faster is announced when the interval has passed, newest text only.
 *
 * @param text The hint, sentence case, at most six words, no full stop.
 * @param status Which icon and accent colour to show.
 * @param modifier Layout of the banner.
 */
@Composable
public fun CcHintBanner(text: String, status: CcStatus, modifier: Modifier = Modifier) {
    val announced = rememberRateLimitedAnnouncement(text)
    val motion = CcTheme.motion
    val camera = CcTheme.colors.camera
    val spacing = CcTheme.spacing
    val accent by animateColorAsState(status.cameraAccent(), tween(motion.standardMillis), label = "CcHintAccent")
    CcScrim(
        modifier = modifier
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = announced
            }
            .heightIn(min = CcTheme.sizes.minTouchTarget),
        shape = CcTheme.shapes.pill,
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = spacing.xl, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            StatusGlyph(status = status, tint = accent)
            Crossfade(
                targetState = text,
                animationSpec = tween(motion.standardMillis),
                label = "CcHintText",
                modifier = Modifier.weight(1f, fill = false),
            ) { shown ->
                Text(
                    text = shown,
                    style = CcTheme.type.hint,
                    color = camera.onScrim,
                    // The banner node carries the rate-limited announcement; the visible text is not read twice.
                    modifier = Modifier.semantics { hideFromAccessibility() },
                )
            }
        }
    }
}

@Composable
private fun StatusGlyph(status: CcStatus, tint: Color) {
    val size = CcTheme.sizes.iconInBanner
    val icon = status.icon()
    if (icon == null) CcProgressRing(color = tint, size = size) else CcIconImage(icon = icon, tint = tint, size = size)
}

/** The text a screen reader should currently hear for [text], changing at most once per interval. */
@Composable
private fun rememberRateLimitedAnnouncement(text: String): String {
    val limiter = remember { AnnouncementRateLimiter() }
    var announced by remember { mutableStateOf(text) }
    LaunchedEffect(text) {
        val wait = limiter.delayBeforeAnnouncing(text, SystemClock.uptimeMillis()) ?: return@LaunchedEffect
        if (wait > 0) delay(wait)
        announced = text
        limiter.onAnnounced(text, SystemClock.uptimeMillis())
    }
    return announced
}

/**
 * Location status on the camera screen. When the problem can be fixed in the settings
 * ([CcLocationState.Off], [CcLocationState.NoPermission]) and [onFix] is given, the whole chip is
 * a button that says so.
 *
 * @param state The location status.
 * @param modifier Layout of the chip.
 * @param onFix Opens the settings that fix the problem; null when no fix is offered.
 */
@Composable
public fun CcLocationChip(state: CcLocationState, modifier: Modifier = Modifier, onFix: (() -> Unit)? = null) {
    val camera = CcTheme.colors.camera
    val spacing = CcTheme.spacing
    val visual = locationVisual(state)
    val fixable = onFix != null && (state == CcLocationState.Off || state == CcLocationState.NoPermission)
    val fixLabel = stringResource(R.string.cc_open_settings)
    val description = if (fixable) "${visual.description}. $fixLabel" else visual.description
    val clickModifier = if (fixable && onFix != null) {
        Modifier.clickable(role = Role.Button, onClickLabel = fixLabel, onClick = onFix)
    } else {
        Modifier
    }
    CcScrim(
        modifier = modifier
            .heightIn(min = CcTheme.sizes.minTouchTarget)
            .then(clickModifier)
            .semantics(mergeDescendants = true) { contentDescription = description },
        shape = CcTheme.shapes.pill,
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = spacing.lg, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            val iconSize = CcTheme.sizes.iconInControl
            if (visual.icon == null) {
                CcProgressRing(color = visual.accent, size = iconSize)
            } else {
                CcIconImage(icon = visual.icon, tint = visual.accent, size = iconSize)
            }
            Text(
                text = visual.label,
                style = CcTheme.type.label,
                color = camera.onScrim,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (fixable) {
                Text(
                    text = fixLabel,
                    style = CcTheme.type.label,
                    color = camera.onScrim,
                    textDecoration = TextDecoration.Underline,
                )
            }
        }
    }
}

private class LocationVisual(val icon: ImageVector?, val accent: Color, val label: String, val description: String)

@Composable
private fun locationVisual(state: CcLocationState): LocationVisual {
    val camera = CcTheme.colors.camera
    return when (state) {
        CcLocationState.Acquiring -> {
            val label = stringResource(R.string.cc_waiting_for_location)
            LocationVisual(null, camera.guideNeutral, label, label)
        }
        is CcLocationState.Ready -> LocationVisual(
            icon = CcIcons.Location,
            accent = camera.guideReady,
            label = stringResource(R.string.cc_location_ready, state.accuracyMeters),
            description = pluralStringResource(
                R.plurals.cc_location_ready_description,
                state.accuracyMeters,
                state.accuracyMeters,
            ),
        )
        is CcLocationState.Weak -> {
            val accuracy = state.accuracyMeters
            if (accuracy == null) {
                val label = stringResource(R.string.cc_location_weak_unknown)
                LocationVisual(CcIcons.Location, camera.guideWarning, label, label)
            } else {
                LocationVisual(
                    icon = CcIcons.Location,
                    accent = camera.guideWarning,
                    label = stringResource(R.string.cc_location_weak, accuracy),
                    description = pluralStringResource(R.plurals.cc_location_weak_description, accuracy, accuracy),
                )
            }
        }
        CcLocationState.Off -> {
            val label = stringResource(R.string.cc_location_off)
            LocationVisual(CcIcons.LocationOff, camera.guideProblem, label, label)
        }
        CcLocationState.NoPermission -> {
            val label = stringResource(R.string.cc_location_no_permission)
            LocationVisual(CcIcons.LocationOff, camera.guideProblem, label, label)
        }
    }
}
