package com.clearcapture.clearcapture.ui.session

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clearcapture.clearcapture.domain.model.FlowOutcome
import com.clearcapture.clearcapture.domain.model.Verdict
import com.clearcapture.clearcapture.ui.R
import com.clearcapture.clearcapture.ui.common.CollectEffects
import com.clearcapture.clearcapture.ui.permission.PermissionIntent
import com.clearcapture.clearcapture.ui.permission.PermissionKind
import kotlinx.coroutines.delay

/**
 * The capture session as one destination: the screen, its ViewModel from [createViewModel], and the platform side of
 * its effects (the one-step permission request, settings screens, recheck on resume, haptics, back). [onFinish] is
 * called once with the session's outcome. [preview] binds the camera; it comes from the SDK's composition root.
 */
@Composable
fun CaptureSessionRoute(
    createViewModel: () -> CaptureSessionViewModel,
    preview: @Composable (modifier: Modifier, onTap: ((Float, Float) -> Unit)?) -> Unit,
    onFinish: (FlowOutcome) -> Unit,
) {
    val vm = viewModel { createViewModel() }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var pendingKinds by remember { mutableStateOf<Set<PermissionKind>>(emptySet()) }
    var keptNumber by remember { mutableStateOf<Int?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val activity = context.findActivity()
        val rationale = results.keys.associateWith { activity?.shouldShowRequestPermissionRationale(it) ?: false }
        vm.onIntent(SessionIntent.Permission(PermissionIntent.RequestResult(pendingKinds, rationale)))
    }
    // Back from settings, a call or another app: permissions, location services and storage are checked again.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onIntent(SessionIntent.Resume) }
    BackHandler { vm.onIntent(if (state.panel != null) SessionIntent.Retake else SessionIntent.Leave) }
    LaunchedEffect(keptNumber) {
        if (keptNumber != null) {
            delay(KEPT_MESSAGE_MILLIS)
            keptNumber = null
        }
    }
    CollectEffects(vm.effects) { effect ->
        when (effect) {
            is SessionEffect.LaunchPermissions -> {
                pendingKinds = effect.kinds
                launcher.launch(effect.permissions.toTypedArray())
            }
            SessionEffect.OpenAppSettings -> context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
            )
            SessionEffect.OpenLocationSettings -> context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            SessionEffect.HapticShutter -> haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            is SessionEffect.HapticVerdict -> haptics.performHapticFeedback(
                when (effect.verdict) {
                    Verdict.PASS -> HapticFeedbackType.Confirm
                    Verdict.WARNING -> HapticFeedbackType.ContextClick
                    Verdict.FAIL -> HapticFeedbackType.Reject
                },
            )
            is SessionEffect.PhotoKept -> keptNumber = effect.number
            is SessionEffect.Finish -> onFinish(effect.outcome)
        }
    }
    CaptureSessionScreen(
        state = state,
        onIntent = vm::onIntent,
        preview = preview,
        kept = keptNumber?.let { stringResource(R.string.cc_session_kept_toast, it) },
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val KEPT_MESSAGE_MILLIS = 1_500L
