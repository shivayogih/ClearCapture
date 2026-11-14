package com.clearcapture.clearcapture.designsystem.preview

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.clearcapture.clearcapture.designsystem.theme.CcTheme
import com.clearcapture.clearcapture.designsystem.theme.ClearCaptureTheme

/** Every state of [component] in one column, in the SDK theme (light or dark from the preview's ui mode). */
@Composable
private fun ComponentStates(component: String) {
    ClearCaptureTheme {
        Column(
            modifier = Modifier.background(CcTheme.colors.surface),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            samplesOf(component).forEach { CcSampleFrame(it) }
        }
    }
}

@Preview(name = "Light", widthDp = 400)
@Preview(name = "Dark", widthDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CcShutterButtonPreview() = ComponentStates("CcShutterButton")

@Preview(name = "Light", widthDp = 400)
@Preview(name = "Dark", widthDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CcHintBannerPreview() = ComponentStates("CcHintBanner")

@Preview(name = "Light", widthDp = 400)
@Preview(name = "Dark", widthDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CcGuideOverlayPreview() = ComponentStates("CcGuideOverlay")

@Preview(name = "Light", widthDp = 400)
@Preview(name = "Dark", widthDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CcLocationChipPreview() = ComponentStates("CcLocationChip")

@Preview(name = "Light", widthDp = 400)
@Preview(name = "Dark", widthDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CcIconButtonPreview() = ComponentStates("CcIconButton")

@Preview(name = "Light", widthDp = 400)
@Preview(name = "Dark", widthDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CcProfileCardPreview() = ComponentStates("CcProfileCard")

@Preview(name = "Light", widthDp = 400)
@Preview(name = "Dark", widthDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CcVerdictBadgePreview() = ComponentStates("CcVerdictBadge")

@Preview(name = "Light", widthDp = 400)
@Preview(name = "Dark", widthDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CcCheckRowPreview() = ComponentStates("CcCheckRow")

@Preview(name = "Light", widthDp = 400)
@Preview(name = "Dark", widthDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CcPrimaryButtonPreview() = ComponentStates("CcPrimaryButton")

@Preview(name = "Light", widthDp = 400)
@Preview(name = "Dark", widthDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CcSecondaryButtonPreview() = ComponentStates("CcSecondaryButton")

@Preview(name = "Light", widthDp = 400)
@Preview(name = "Dark", widthDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CcPermissionCardPreview() = ComponentStates("CcPermissionCard")

@Preview(name = "Light", widthDp = 400)
@Preview(name = "Dark", widthDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CcStatusScreenPreview() = ComponentStates("CcStatusScreen")

@Preview(name = "Light", widthDp = 400)
@Preview(name = "Dark", widthDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CcTextFieldPreview() = ComponentStates("CcTextField")

@Preview(name = "Light", widthDp = 400)
@Preview(name = "Dark", widthDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CcScrimPreview() = ComponentStates("CcScrim")
