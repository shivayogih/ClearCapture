package com.clearcapture.clearcapture.gallery

import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.clearcapture.clearcapture.R
import com.clearcapture.clearcapture.designsystem.components.CcCheckRow
import com.clearcapture.clearcapture.designsystem.components.CcCheckStatus
import com.clearcapture.clearcapture.designsystem.components.CcGuideOverlay
import com.clearcapture.clearcapture.designsystem.components.CcGuideRect
import com.clearcapture.clearcapture.designsystem.components.CcGuideShape
import com.clearcapture.clearcapture.designsystem.components.CcHintBanner
import com.clearcapture.clearcapture.designsystem.components.CcIconButton
import com.clearcapture.clearcapture.designsystem.components.CcIconButtonStyle
import com.clearcapture.clearcapture.designsystem.components.CcLocationChip
import com.clearcapture.clearcapture.designsystem.components.CcLocationState
import com.clearcapture.clearcapture.designsystem.components.CcPermissionCard
import com.clearcapture.clearcapture.designsystem.components.CcPermissionState
import com.clearcapture.clearcapture.designsystem.components.CcPrimaryButton
import com.clearcapture.clearcapture.designsystem.components.CcProfileCard
import com.clearcapture.clearcapture.designsystem.components.CcScrim
import com.clearcapture.clearcapture.designsystem.components.CcSecondaryButton
import com.clearcapture.clearcapture.designsystem.components.CcShutterButton
import com.clearcapture.clearcapture.designsystem.components.CcShutterState
import com.clearcapture.clearcapture.designsystem.components.CcStatus
import com.clearcapture.clearcapture.designsystem.components.CcStatusKind
import com.clearcapture.clearcapture.designsystem.components.CcStatusScreen
import com.clearcapture.clearcapture.designsystem.components.CcTextField
import com.clearcapture.clearcapture.designsystem.components.CcVerdict
import com.clearcapture.clearcapture.designsystem.components.CcVerdictBadge
import com.clearcapture.clearcapture.designsystem.icons.CcIcons
import com.clearcapture.clearcapture.designsystem.theme.CcAppearance
import com.clearcapture.clearcapture.designsystem.theme.CcBrandColorRejection
import com.clearcapture.clearcapture.designsystem.theme.CcCornerStyle
import com.clearcapture.clearcapture.designsystem.theme.CcDarkMode
import com.clearcapture.clearcapture.designsystem.theme.CcTheme
import com.clearcapture.clearcapture.designsystem.theme.ClearCaptureTheme
import com.clearcapture.clearcapture.designsystem.R as DsR

private const val TAG = "CcGallery"

/** Brand colour presets; the last one fails the 4.5:1 rule on purpose. */
internal enum class BrandPreset(@StringRes val label: Int, val brand: Long?, val onBrand: Long?) {
    Default(R.string.gallery_brand_default, null, null),
    Ocean(R.string.gallery_brand_ocean, 0xFF1F4FBF, 0xFFFFFFFF),
    Plum(R.string.gallery_brand_plum, 0xFF6B2FA0, 0xFFFFFFFF),
    Sun(R.string.gallery_brand_sun_fails, 0xFFFFD54F, 0xFFFFFFFF),
}

/** Font presets. */
internal enum class FontPreset(@StringRes val label: Int, val family: FontFamily?) {
    System(R.string.gallery_font_system, null),
    Serif(R.string.gallery_font_serif, FontFamily.Serif),
    Monospace(R.string.gallery_font_mono, FontFamily.Monospace),
}

/**
 * The POC's component gallery (LLD section 14): every design system component in every state,
 * with controls for light or dark and for each appearance option a host can set.
 */
@Composable
internal fun GalleryScreen(modifier: Modifier = Modifier) {
    var dark by rememberSaveable { mutableStateOf(false) }
    var brand by rememberSaveable { mutableStateOf(BrandPreset.Default) }
    var square by rememberSaveable { mutableStateOf(false) }
    var font by rememberSaveable { mutableStateOf(FontPreset.System) }
    var rejection by rememberSaveable { mutableStateOf<String?>(null) }
    val appearance = CcAppearance(
        brandColor = brand.brand,
        onBrandColor = brand.onBrand,
        darkMode = if (dark) CcDarkMode.DARK else CcDarkMode.LIGHT,
        cornerStyle = if (square) CcCornerStyle.SQUARE else CcCornerStyle.ROUNDED,
        fontFamily = font.family,
    )
    val onRejected: (CcBrandColorRejection) -> Unit = { refused ->
        Log.e(TAG, "Brand colours refused: $refused")
        rejection = "%.2f".format(refused.contrastRatio ?: 0.0)
    }
    ClearCaptureTheme(appearance = appearance, onBrandColorRejected = onRejected) {
        val spacing = CcTheme.spacing
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(CcTheme.colors.surface),
            contentPadding = WindowInsets.safeDrawing.asPaddingValues(),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            item {
                Controls(
                    dark = dark,
                    onDark = { dark = it },
                    brand = brand,
                    onBrand = {
                        brand = it
                        rejection = null
                    },
                    square = square,
                    onSquare = { square = it },
                    font = font,
                    onFont = { font = it },
                    rejection = rejection.takeIf { brand == BrandPreset.Sun },
                )
            }
            cameraSections()
            cameraControlSections()
            reviewSections()
            inputSections()
            stateSections()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Controls(
    dark: Boolean,
    onDark: (Boolean) -> Unit,
    brand: BrandPreset,
    onBrand: (BrandPreset) -> Unit,
    square: Boolean,
    onSquare: (Boolean) -> Unit,
    font: FontPreset,
    onFont: (FontPreset) -> Unit,
    rejection: String?,
) {
    val colors = CcTheme.colors
    val spacing = CcTheme.spacing
    Column(
        modifier = Modifier.padding(horizontal = spacing.screenEdge),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Text(
            text = stringResource(R.string.gallery_title),
            style = CcTheme.type.title,
            color = colors.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            Text(stringResource(R.string.gallery_dark), style = CcTheme.type.body, color = colors.onSurface)
            Switch(checked = dark, onCheckedChange = onDark)
            Text(stringResource(R.string.gallery_square), style = CcTheme.type.body, color = colors.onSurface)
            Switch(checked = square, onCheckedChange = onSquare)
        }
        Text(stringResource(R.string.gallery_brand), style = CcTheme.type.label, color = colors.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            BrandPreset.entries.forEach { preset ->
                FilterChip(
                    selected = preset == brand,
                    onClick = { onBrand(preset) },
                    label = { Text(stringResource(preset.label)) },
                )
            }
        }
        if (rejection != null) {
            Text(
                text = stringResource(R.string.gallery_brand_refused, rejection),
                style = CcTheme.type.body,
                color = colors.error,
            )
        }
        Text(stringResource(R.string.gallery_font), style = CcTheme.type.label, color = colors.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            FontPreset.entries.forEach { preset ->
                FilterChip(
                    selected = preset == font,
                    onClick = { onFont(preset) },
                    label = { Text(stringResource(preset.label)) },
                )
            }
        }
    }
}

private fun LazyListScope.section(@StringRes title: Int, content: @Composable () -> Unit) {
    item {
        Column(verticalArrangement = Arrangement.spacedBy(CcTheme.spacing.sm)) {
            Text(
                text = stringResource(title),
                style = CcTheme.type.bodyStrong,
                color = CcTheme.colors.onSurface,
                modifier = Modifier
                    .padding(horizontal = CcTheme.spacing.screenEdge)
                    .semantics { heading() },
            )
            content()
        }
    }
}

/** A stand-in for the camera preview: white (the worst case for the scrim) and a dark scene. */
@Composable
private fun CameraStage(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.matchParentSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color.White))
            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(SceneDark))
        }
        Column(
            modifier = Modifier.padding(CcTheme.spacing.screenEdge),
            verticalArrangement = Arrangement.spacedBy(CcTheme.spacing.sm),
        ) { content() }
    }
}

private val SceneDark = Color(0xFF3A4046)

@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.cameraSections() {
    section(R.string.gallery_hint_banner) {
        CameraStage {
            CcHintBanner(stringResource(DsR.string.cc_waiting_for_location), CcStatus.Neutral)
            CcHintBanner(stringResource(DsR.string.cc_status_ready), CcStatus.Ready)
            CcHintBanner(stringResource(DsR.string.cc_hint_too_dark), CcStatus.Warning)
            CcHintBanner(stringResource(DsR.string.cc_hint_fit_container), CcStatus.Problem)
        }
    }
    section(R.string.gallery_location_chip) {
        CameraStage {
            CcLocationChip(CcLocationState.Acquiring)
            CcLocationChip(CcLocationState.Ready(accuracyMeters = 4))
            CcLocationChip(CcLocationState.Weak(accuracyMeters = 40))
            CcLocationChip(CcLocationState.Off, onFix = {})
            CcLocationChip(CcLocationState.NoPermission, onFix = {})
        }
    }
    section(R.string.gallery_shutter) {
        CameraStage {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(CcTheme.spacing.sm)) {
                CcShutterButton(CcShutterState.Enabled, onClick = {})
                CcShutterButton(
                    CcShutterState.Disabled,
                    onClick = {},
                    disabledReason = stringResource(DsR.string.cc_hint_fit_container),
                )
                CcShutterButton(CcShutterState.Capturing, onClick = {})
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.cameraControlSections() {
    section(R.string.gallery_icon_button) {
        CameraStage {
            var torch by rememberSaveable { mutableStateOf(false) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(CcTheme.spacing.sm)) {
                CcIconButton(CcIcons.Close, stringResource(DsR.string.cc_close), onClick = {})
                CcIconButton(
                    icon = if (torch) CcIcons.TorchOn else CcIcons.TorchOff,
                    contentDescription = stringResource(DsR.string.cc_torch),
                    onClick = { torch = !torch },
                    toggled = torch,
                )
                CcIconButton(CcIcons.Close, stringResource(DsR.string.cc_close), onClick = {}, enabled = false)
                CcIconButton(CcIcons.Back, stringResource(DsR.string.cc_back), {}, style = CcIconButtonStyle.Surface)
                CcIconButton(
                    CcIcons.TorchOn,
                    stringResource(DsR.string.cc_torch),
                    {},
                    toggled = true,
                    style = CcIconButtonStyle.Surface,
                )
                CcIconButton(
                    CcIcons.Back,
                    stringResource(DsR.string.cc_back),
                    {},
                    enabled = false,
                    style = CcIconButtonStyle.Surface,
                )
            }
        }
    }
    section(R.string.gallery_guide) {
        val guide = CcGuideRect(left = 0.12f, top = 0.12f, right = 0.88f, bottom = 0.88f)
        Column(verticalArrangement = Arrangement.spacedBy(CcTheme.spacing.sm)) {
            CcGuideShape.entries.forEach { shape ->
                FlowRow(
                    modifier = Modifier.padding(horizontal = CcTheme.spacing.screenEdge),
                    horizontalArrangement = Arrangement.spacedBy(CcTheme.spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(CcTheme.spacing.sm),
                ) {
                    val statuses = if (shape == CcGuideShape.NONE) listOf(CcStatus.Ready) else CcStatus.entries
                    statuses.forEach { status ->
                        Box(modifier = Modifier.width(160.dp).height(120.dp)) {
                            Row(modifier = Modifier.matchParentSize()) {
                                Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color.White))
                                Box(modifier = Modifier.weight(1f).fillMaxHeight().background(SceneDark))
                            }
                            CcGuideOverlay(shape, status, guide, Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
    section(R.string.gallery_scrim) {
        CameraStage { CcScrim(modifier = Modifier.size(width = 240.dp, height = 64.dp), shape = CcTheme.shapes.pill) }
    }
}

private fun LazyListScope.reviewSections() {
    section(R.string.gallery_verdict) {
        Column(
            modifier = Modifier.padding(horizontal = CcTheme.spacing.screenEdge),
            verticalArrangement = Arrangement.spacedBy(CcTheme.spacing.sm),
        ) { CcVerdict.entries.forEach { CcVerdictBadge(it) } }
    }
    section(R.string.gallery_check_row) {
        Column(
            modifier = Modifier.padding(horizontal = CcTheme.spacing.screenEdge),
            verticalArrangement = Arrangement.spacedBy(CcTheme.spacing.sm),
        ) {
            CcCheckRow(
                stringResource(R.string.gallery_check_sharpness),
                CcCheckStatus.Pass,
                reason = stringResource(R.string.gallery_clear),
            )
            CcCheckRow(
                stringResource(R.string.gallery_check_glare),
                CcCheckStatus.Warning,
                reason = stringResource(R.string.gallery_glare_reason),
                advice = stringResource(R.string.gallery_glare_advice),
            )
            CcCheckRow(
                stringResource(R.string.gallery_check_sharpness),
                CcCheckStatus.Fail,
                reason = stringResource(DsR.string.cc_reason_blurred),
            )
        }
    }
    section(R.string.gallery_buttons) {
        Column(
            modifier = Modifier.padding(horizontal = CcTheme.spacing.screenEdge),
            verticalArrangement = Arrangement.spacedBy(CcTheme.spacing.sm),
        ) {
            val accept = stringResource(R.string.gallery_accept)
            val retake = stringResource(DsR.string.cc_status_retake)
            CcPrimaryButton(accept, onClick = {})
            CcPrimaryButton(accept, onClick = {}, enabled = false)
            CcPrimaryButton(accept, onClick = {}, loading = true)
            CcSecondaryButton(retake, onClick = {})
            CcSecondaryButton(retake, onClick = {}, enabled = false)
        }
    }
}

private fun LazyListScope.inputSections() {
    section(R.string.gallery_profile_card) {
        var selected by rememberSaveable { mutableStateOf(0) }
        Column(
            modifier = Modifier.padding(horizontal = CcTheme.spacing.screenEdge),
            verticalArrangement = Arrangement.spacedBy(CcTheme.spacing.sm),
        ) {
            listOf(
                Triple(
                    R.string.gallery_profile_container,
                    R.string.gallery_profile_container_body,
                    CcIcons.Container,
                ),
                Triple(R.string.gallery_profile_portrait, R.string.gallery_profile_portrait_body, CcIcons.Person),
                Triple(R.string.gallery_profile_vehicle, R.string.gallery_profile_vehicle_body, CcIcons.Vehicle),
            ).forEachIndexed { index, (title, body, icon) ->
                CcProfileCard(
                    title = stringResource(title),
                    description = stringResource(body),
                    selected = index == selected,
                    onClick = { selected = index },
                    icon = icon,
                )
            }
        }
    }
    section(R.string.gallery_text_field) {
        var reason by rememberSaveable { mutableStateOf("") }
        Column(
            modifier = Modifier.padding(horizontal = CcTheme.spacing.screenEdge),
            verticalArrangement = Arrangement.spacedBy(CcTheme.spacing.sm),
        ) {
            val label = stringResource(R.string.gallery_reason_label)
            CcTextField(value = reason, onValueChange = { reason = it }, label = label, maxLength = 200)
            CcTextField(
                value = "Ok",
                onValueChange = {},
                label = label,
                maxLength = 200,
                errorMessage = stringResource(R.string.gallery_reason_error),
            )
        }
    }
}

private fun LazyListScope.stateSections() {
    section(R.string.gallery_permission_card) {
        Column(
            modifier = Modifier.padding(horizontal = CcTheme.spacing.screenEdge),
            verticalArrangement = Arrangement.spacedBy(CcTheme.spacing.sm),
        ) {
            CcPermissionState.entries.forEach { state ->
                CcPermissionCard(
                    title = stringResource(R.string.gallery_permission_camera),
                    reason = stringResource(R.string.gallery_permission_camera_reason),
                    state = state,
                    onAction = {},
                    icon = CcIcons.Camera,
                )
            }
        }
    }
    section(R.string.gallery_status_screen) {
        Column(verticalArrangement = Arrangement.spacedBy(CcTheme.spacing.sm)) {
            Box(modifier = Modifier.fillMaxWidth().height(420.dp)) {
                CcStatusScreen(
                    kind = CcStatusKind.Info,
                    title = stringResource(R.string.gallery_status_info_title),
                    message = stringResource(R.string.gallery_status_info_message),
                    actionLabel = stringResource(DsR.string.cc_open_settings),
                    onAction = {},
                )
            }
            Box(modifier = Modifier.fillMaxWidth().height(420.dp)) {
                CcStatusScreen(
                    kind = CcStatusKind.Error,
                    title = stringResource(R.string.gallery_status_error_title),
                    message = stringResource(R.string.gallery_status_error_message),
                    actionLabel = stringResource(R.string.gallery_try_again),
                    onAction = {},
                )
            }
        }
    }
}

@Preview
@Composable
private fun GalleryScreenPreview() {
    GalleryScreen()
}
