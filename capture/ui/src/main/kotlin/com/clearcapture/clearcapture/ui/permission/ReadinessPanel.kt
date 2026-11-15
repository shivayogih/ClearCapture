package com.clearcapture.clearcapture.ui.permission

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.clearcapture.clearcapture.designsystem.components.CcPermissionCard
import com.clearcapture.clearcapture.designsystem.components.CcPermissionState
import com.clearcapture.clearcapture.designsystem.components.CcPrimaryButton
import com.clearcapture.clearcapture.designsystem.components.CcSecondaryButton
import com.clearcapture.clearcapture.designsystem.icons.CcIcons
import com.clearcapture.clearcapture.designsystem.theme.CcTheme
import com.clearcapture.clearcapture.ui.R
import com.clearcapture.clearcapture.ui.common.BodyText
import com.clearcapture.clearcapture.ui.common.TitleText
import com.clearcapture.clearcapture.designsystem.R as DsR

/**
 * Everything that must be fixed before the camera can open, listed together on the capture screen
 * (SDK_REQUIREMENTS 7.0): one card per permission with its state, then the one main action. Stateless.
 */
@Composable
fun ReadinessPanel(state: PermissionUiState, onIntent: (PermissionIntent) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CcTheme.colors.surface)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(CcTheme.spacing.screenEdge),
        verticalArrangement = Arrangement.spacedBy(CcTheme.spacing.lg),
    ) {
        TitleText(stringResource(R.string.cc_permission_title))
        CcPermissionCard(
            title = stringResource(R.string.cc_permission_camera_title),
            reason = stringResource(R.string.cc_permission_camera_reason),
            state = state.camera.toCard(),
            onAction = { onIntent(PermissionIntent.CardAction(PermissionKind.CAMERA)) },
            icon = CcIcons.Camera,
        )
        state.location?.let { location ->
            CcPermissionCard(
                title = stringResource(R.string.cc_permission_location_title),
                reason = stringResource(
                    if (state.locationOptional) R.string.cc_permission_location_reason_optional else R.string.cc_permission_location_reason,
                ),
                state = location.toCard(),
                onAction = { onIntent(PermissionIntent.CardAction(PermissionKind.LOCATION)) },
                icon = CcIcons.Location,
            )
        }
        if (state.step != PermissionStep.Done) {
            BodyText(stringResource(state.step.message()))
            CcPrimaryButton(
                text = stringResource(state.step.action()),
                onClick = { onIntent(PermissionIntent.PrimaryAction) },
                modifier = Modifier.fillMaxWidth(),
            )
            CcSecondaryButton(
                text = stringResource(R.string.cc_permission_not_now),
                onClick = { onIntent(PermissionIntent.Leave) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun Grant.toCard(): CcPermissionState = when (this) {
    Grant.GRANTED -> CcPermissionState.Granted
    Grant.NOT_ASKED -> CcPermissionState.Default
    Grant.DENIED, Grant.APPROXIMATE_ONLY -> CcPermissionState.Denied
    Grant.PERMANENTLY_DENIED, Grant.PRECISE_REFUSED -> CcPermissionState.PermanentlyDenied
}

private fun PermissionStep.message(): Int = when (this) {
    is PermissionStep.Explain -> R.string.cc_permission_step_explain_all
    is PermissionStep.Denied ->
        if (kind == PermissionKind.CAMERA) R.string.cc_permission_step_denied_camera else R.string.cc_permission_step_denied_location
    is PermissionStep.PermanentlyDenied -> R.string.cc_permission_step_permanent
    PermissionStep.PreciseNeeded -> R.string.cc_permission_step_precise
    PermissionStep.PreciseRefused -> R.string.cc_permission_step_precise_refused
    PermissionStep.LocationServicesOff -> R.string.cc_permission_step_location_off
    PermissionStep.Done -> R.string.cc_permission_step_explain_all
}

private fun PermissionStep.action(): Int = when (this) {
    is PermissionStep.Explain -> R.string.cc_permission_action_allow
    is PermissionStep.Denied -> R.string.cc_permission_action_ask_again
    is PermissionStep.PermanentlyDenied, PermissionStep.PreciseRefused -> DsR.string.cc_open_settings
    PermissionStep.PreciseNeeded -> R.string.cc_permission_action_precise
    PermissionStep.LocationServicesOff -> R.string.cc_permission_action_location_settings
    PermissionStep.Done -> R.string.cc_permission_action_allow
}
