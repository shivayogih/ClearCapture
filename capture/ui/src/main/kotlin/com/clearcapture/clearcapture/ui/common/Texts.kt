package com.clearcapture.clearcapture.ui.common

import android.annotation.SuppressLint
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import com.clearcapture.clearcapture.designsystem.theme.CcTheme
import com.clearcapture.clearcapture.domain.hint.HintKind
import com.clearcapture.clearcapture.domain.model.GuideShape
import com.clearcapture.clearcapture.domain.model.QualityCheck
import com.clearcapture.clearcapture.ui.R
import com.clearcapture.clearcapture.ui.camera.LocationChipUi

/** Text in a design-system style. Feature modules never use Material text directly. */
@Composable
internal fun CcText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    BasicText(text = text, modifier = modifier, style = style.copy(color = color))
}

/** Body text on the screen surface. */
@Composable
internal fun BodyText(text: String, modifier: Modifier = Modifier) =
    CcText(text, CcTheme.type.body, CcTheme.colors.onSurface, modifier)

/** A screen title. */
@Composable
internal fun TitleText(text: String, modifier: Modifier = Modifier) =
    CcText(text, CcTheme.type.title, CcTheme.colors.onSurface, modifier)

/**
 * Resolves a profile's `titleKey` to its string. Profiles are data, possibly supplied by the host, so the key is a
 * resource name looked up at run time; an unknown key falls back to the key itself.
 */
@SuppressLint("DiscouragedApi") // The key comes from profile data, so it cannot be a compile-time resource ID.
@Composable
internal fun profileTitle(titleKey: String): String {
    val packageName = LocalContext.current.packageName
    val id = LocalResources.current.getIdentifier(titleKey, "string", packageName)
    return if (id != 0) stringResource(id) else titleKey
}

/** The one-line description under a profile card. */
@Composable
internal fun profileDescription(shape: GuideShape, id: String): String = stringResource(
    when (id) {
        "container" -> R.string.cc_profile_container_description
        "person" -> R.string.cc_profile_person_description
        "vehicle" -> R.string.cc_profile_vehicle_description
        "general" -> R.string.cc_profile_general_description
        else -> if (shape == GuideShape.NONE) R.string.cc_profile_general_description else R.string.cc_profile_custom_description
    },
)

/** The live hint text (at most six words, DESIGN_SYSTEM section 3). */
@Composable
internal fun hintText(kind: HintKind, location: LocationChipUi, shape: GuideShape?): String = stringResource(
    when (kind) {
        HintKind.LOCATION_BLOCKED -> when (location) {
            LocationChipUi.Off -> R.string.cc_hint_location_off
            LocationChipUi.NoPermission -> R.string.cc_hint_location_permission
            else -> R.string.cc_hint_location_waiting
        }
        HintKind.ANALYZER_UNAVAILABLE -> R.string.cc_hint_analyzer_unavailable
        HintKind.TOO_DARK -> R.string.cc_hint_dark
        HintKind.TOO_BRIGHT -> R.string.cc_hint_bright
        HintKind.BLUR -> R.string.cc_hint_steady
        HintKind.GLARE -> R.string.cc_hint_glare
        HintKind.SUBJECT_CUT_OFF -> if (shape == GuideShape.CONTAINER) R.string.cc_hint_cut_off_container else R.string.cc_hint_cut_off
        HintKind.SUBJECT_TOO_SMALL -> R.string.cc_hint_small
        HintKind.SUBJECT_TOO_CLOSE -> R.string.cc_hint_close
        HintKind.OBSTRUCTION -> R.string.cc_hint_obstruction
        HintKind.READY -> R.string.cc_hint_ready
        HintKind.NONE -> R.string.cc_hint_aim
    },
)

/** The name of a check on the review screen. */
@Composable
internal fun checkName(check: QualityCheck): String = stringResource(
    when (check) {
        QualityCheck.BLUR -> R.string.cc_check_blur
        QualityCheck.EXPOSURE -> R.string.cc_check_exposure
        QualityCheck.GLARE -> R.string.cc_check_glare
        QualityCheck.FRAMING -> R.string.cc_check_framing
        QualityCheck.RESOLUTION -> R.string.cc_check_resolution
        QualityCheck.OBSTRUCTION -> R.string.cc_check_obstruction
        QualityCheck.LOCATION -> R.string.cc_check_location
    },
)
