package com.clearcapture.clearcapture.ui.navigation

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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.clearcapture.clearcapture.domain.model.FlowOutcome
import com.clearcapture.clearcapture.domain.model.Verdict
import com.clearcapture.clearcapture.ui.camera.CameraEffect
import com.clearcapture.clearcapture.ui.camera.CameraIntent
import com.clearcapture.clearcapture.ui.camera.CameraScreen
import com.clearcapture.clearcapture.ui.common.CollectEffects
import com.clearcapture.clearcapture.ui.permission.PermissionEffect
import com.clearcapture.clearcapture.ui.permission.PermissionIntent
import com.clearcapture.clearcapture.ui.permission.PermissionScreen
import com.clearcapture.clearcapture.ui.profile.ProfileEffect
import com.clearcapture.clearcapture.ui.profile.ProfileIntent
import com.clearcapture.clearcapture.ui.profile.ProfileScreen
import com.clearcapture.clearcapture.ui.review.ReviewEffect
import com.clearcapture.clearcapture.ui.review.ReviewIntent
import com.clearcapture.clearcapture.ui.review.ReviewScreen
import com.clearcapture.clearcapture.ui.text.ReasonText
import kotlinx.coroutines.delay

private const val PERMISSION = "cc_permission"
private const val PROFILE = "cc_profile"
private const val CAMERA = "cc_camera/{profileId}"
private const val REVIEW = "cc_review/{captureId}"
private const val MESSAGE_MILLIS = 3_000L

/** The whole capture flow: permission → profile → camera/{profileId} → review/{captureId} (LLD 4.4). */
@Composable
fun CaptureNavGraph(host: CaptureFlowHost, navController: NavHostController = rememberNavController()) {
    val start = when (val dest = host.startDestination) {
        is CaptureDestination.Permission -> PERMISSION
        CaptureDestination.Profile -> PROFILE
        is CaptureDestination.Camera -> "cc_camera/${dest.profileId}"
    }
    val requestedProfile = (host.startDestination as? CaptureDestination.Permission)?.profileId
    NavHost(navController = navController, startDestination = start) {
        composable(PERMISSION) {
            PermissionRoute(host) {
                host.onPermissionsGranted()
                navController.navigate(requestedProfile?.let { "cc_camera/$it" } ?: PROFILE) {
                    popUpTo(PERMISSION) { inclusive = true }
                }
            }
        }
        composable(PROFILE) { ProfileRoute(host) { navController.navigate("cc_camera/$it") } }
        composable(CAMERA, arguments = listOf(navArgument("profileId") { type = NavType.StringType })) { entry ->
            val profileId = entry.arguments?.getString("profileId").orEmpty()
            CameraRoute(
                host = host,
                profileId = profileId,
                onReview = { navController.navigate("cc_review/$it") },
                onBack = { if (!navController.popBackStack(PROFILE, inclusive = false)) host.finish(FlowOutcome.Cancelled) },
            )
        }
        composable(REVIEW, arguments = listOf(navArgument("captureId") { type = NavType.StringType })) { entry ->
            ReviewRoute(host, entry.arguments?.getString("captureId").orEmpty()) { navController.popBackStack() }
        }
    }
}

@Composable
private fun PermissionRoute(host: CaptureFlowHost, onGranted: () -> Unit) {
    val vm = viewModel { host.permissionViewModel() }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingKind by remember { mutableStateOf<com.clearcapture.clearcapture.ui.permission.PermissionKind?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val activity = context.findActivity()
        val kind = pendingKind ?: return@rememberLauncherForActivityResult
        val rationale = results.keys.associateWith { activity?.shouldShowRequestPermissionRationale(it) ?: false }
        vm.onIntent(PermissionIntent.RequestResult(kind, rationale))
    }
    // Returning from system settings re-reads the state (SDK_REQUIREMENTS 5.3: "on return, rechecks automatically").
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onIntent(PermissionIntent.Recheck) }
    BackHandler { vm.onIntent(PermissionIntent.Leave) }
    CollectEffects(vm.effects) { effect ->
        when (effect) {
            is PermissionEffect.LaunchRequest -> {
                pendingKind = effect.kind
                launcher.launch(effect.permissions.toTypedArray())
            }
            PermissionEffect.OpenAppSettings -> context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
            )
            PermissionEffect.OpenLocationSettings -> context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            PermissionEffect.Granted -> onGranted()
            PermissionEffect.Cancelled -> host.finish(FlowOutcome.Cancelled)
        }
    }
    PermissionScreen(state, vm::onIntent)
}

@Composable
private fun ProfileRoute(host: CaptureFlowHost, onSelected: (String) -> Unit) {
    val vm = viewModel { host.profileViewModel() }
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler { vm.onIntent(ProfileIntent.Back) }
    CollectEffects(vm.effects) { effect ->
        when (effect) {
            is ProfileEffect.NavigateToCamera -> onSelected(effect.profileId)
            ProfileEffect.Cancelled -> host.finish(FlowOutcome.Cancelled)
        }
    }
    ProfileScreen(state, vm::onIntent)
}

@Composable
private fun CameraRoute(host: CaptureFlowHost, profileId: String, onReview: (String) -> Unit, onBack: () -> Unit) {
    val vm = viewModel { host.cameraViewModel() }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var messageRes by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(profileId) { vm.onIntent(CameraIntent.Start(profileId)) }
    LaunchedEffect(messageRes) {
        if (messageRes != null) {
            delay(MESSAGE_MILLIS)
            messageRes = null
        }
    }
    BackHandler { vm.onIntent(CameraIntent.BackPressed) }
    CollectEffects(vm.effects) { effect ->
        when (effect) {
            is CameraEffect.NavigateToReview -> onReview(effect.captureId)
            CameraEffect.NavigateBack -> onBack()
            CameraEffect.OpenLocationSettings -> context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            CameraEffect.HapticShutter -> haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            is CameraEffect.ShowError -> messageRes = ReasonText.of(effect.error)
        }
    }
    CameraScreen(
        state = state,
        onIntent = vm::onIntent,
        preview = { modifier, onTap -> host.CameraPreview(modifier, onTap) },
        message = messageRes?.let { stringResource(it) },
    )
}

@Composable
private fun ReviewRoute(host: CaptureFlowHost, captureId: String, onBackToCamera: () -> Unit) {
    val vm = viewModel { host.reviewViewModel() }
    val state by vm.state.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(captureId) { vm.onIntent(ReviewIntent.Load(captureId)) }
    BackHandler { vm.onIntent(ReviewIntent.Retake) }
    CollectEffects(vm.effects) { effect ->
        when (effect) {
            is ReviewEffect.Finish -> host.finish(effect.outcome)
            ReviewEffect.BackToCamera -> onBackToCamera()
            is ReviewEffect.HapticVerdict -> haptics.performHapticFeedback(
                when (effect.verdict) {
                    Verdict.PASS -> HapticFeedbackType.Confirm
                    Verdict.WARNING -> HapticFeedbackType.ContextClick
                    Verdict.FAIL -> HapticFeedbackType.Reject
                },
            )
        }
    }
    ReviewScreen(state, vm::onIntent)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
