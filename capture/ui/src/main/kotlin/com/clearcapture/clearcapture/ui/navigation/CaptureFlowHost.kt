package com.clearcapture.clearcapture.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.clearcapture.clearcapture.domain.model.FlowOutcome
import com.clearcapture.clearcapture.ui.camera.CameraViewModel
import com.clearcapture.clearcapture.ui.permission.PermissionViewModel
import com.clearcapture.clearcapture.ui.profile.ProfileViewModel
import com.clearcapture.clearcapture.ui.review.ReviewViewModel

/**
 * What the capture flow needs from the SDK's composition root (`:capture:api`). The UI module builds no objects
 * itself: every ViewModel and the camera preview come from here, so the graph is wired in one place.
 */
interface CaptureFlowHost {
    /** Where the flow starts. */
    val startDestination: CaptureDestination

    /** A new permission ViewModel. */
    fun permissionViewModel(): PermissionViewModel

    /** A new profile ViewModel. */
    fun profileViewModel(): ProfileViewModel

    /** A new camera ViewModel. */
    fun cameraViewModel(): CameraViewModel

    /** A new review ViewModel. */
    fun reviewViewModel(): ReviewViewModel

    /** The permission step finished: start location and OpenCV warm-up before the camera opens. */
    fun onPermissionsGranted()

    /** The flow ended. Called exactly once. */
    fun finish(outcome: FlowOutcome)

    /** The live camera preview, bound to the current lifecycle. */
    @Composable
    fun CameraPreview(modifier: Modifier, onTap: ((Float, Float) -> Unit)?)
}

/** The first screen of a flow. */
sealed interface CaptureDestination {
    /** Permissions are missing and the SDK asks for them. */
    data class Permission(val profileId: String?) : CaptureDestination

    /** The user chooses a profile. */
    data object Profile : CaptureDestination

    /** The host chose the profile. */
    data class Camera(val profileId: String) : CaptureDestination
}
