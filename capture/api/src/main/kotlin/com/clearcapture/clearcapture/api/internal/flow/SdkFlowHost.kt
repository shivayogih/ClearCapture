package com.clearcapture.clearcapture.api.internal.flow

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.clearcapture.clearcapture.camera.CameraPreview
import com.clearcapture.clearcapture.domain.model.CaptureProfile
import com.clearcapture.clearcapture.domain.model.FailureCode
import com.clearcapture.clearcapture.domain.model.FlowOutcome
import com.clearcapture.clearcapture.ui.camera.CameraDependencies
import com.clearcapture.clearcapture.ui.camera.CameraViewModel
import com.clearcapture.clearcapture.ui.navigation.CaptureDestination
import com.clearcapture.clearcapture.ui.navigation.CaptureFlowHost
import com.clearcapture.clearcapture.ui.permission.PermissionViewModel
import com.clearcapture.clearcapture.ui.profile.ProfileViewModel
import com.clearcapture.clearcapture.ui.review.AcceptPolicy
import com.clearcapture.clearcapture.ui.review.ReviewViewModel

/** Builds every screen's ViewModel from the flow graph (the `CaptureViewModelFactory` of LLD 10). */
internal class SdkFlowHost(
    private val graph: FlowGraph,
    override val startDestination: CaptureDestination,
    private val profiles: () -> List<CaptureProfile>,
    private val onFinish: (FlowOutcome) -> Unit,
) : CaptureFlowHost {

    private var finished = false

    override fun permissionViewModel(): PermissionViewModel =
        PermissionViewModel(locationRequired = locationRequired(), snapshot = graph::permissionSnapshot)

    override fun profileViewModel(): ProfileViewModel = ProfileViewModel(graph.getProfiles)

    override fun cameraViewModel(): CameraViewModel = CameraViewModel(
        CameraDependencies(
            getProfiles = graph.getProfiles,
            camera = graph.camera,
            guidance = graph.observeGuidance::invoke,
            capture = graph::capture,
            onActiveProfile = { profile ->
                graph.pipeline.setProfile(profile)
                profile?.let { graph.startSession(it.location) }
            },
            onProfileMissing = { finish(FlowOutcome.Failed(FailureCode.PROFILE_NOT_FOUND, "Unknown profile")) },
        ),
    )

    override fun reviewViewModel(): ReviewViewModel = ReviewViewModel(graph.pending, graph.accept, graph.discard) { capture ->
        val profile = profiles().firstOrNull { it.id == capture.profileId }
        AcceptPolicy(
            allowAcceptOnWarning = graph.config.allowAcceptOnWarning ?: profile?.allowAcceptOnWarning ?: true,
            allowOverrideOnFail = graph.config.allowOverrideOnFail ?: profile?.allowOverrideOnFail ?: false,
        )
    }

    override fun onPermissionsGranted() {
        sessionPolicy()?.let(graph::startSession)
    }

    override fun finish(outcome: FlowOutcome) {
        if (finished) return
        finished = true
        onFinish(outcome)
    }

    @Composable
    override fun CameraPreview(modifier: Modifier, onTap: ((Float, Float) -> Unit)?) {
        CameraPreview(controller = graph.camera, frameConsumer = graph.pipeline, onTap = onTap, modifier = modifier)
    }

    /** Location is needed if the chosen profile needs it, or, before a choice, if any profile does. */
    private fun locationRequired(): Boolean {
        val all = profiles()
        val chosen = graph.config.profileId?.let { id -> all.firstOrNull { it.id == id } }
        return chosen?.location?.required ?: all.any { it.location.required }
    }

    /** The chosen profile's location rules; before a choice, those of the first profile that needs location. */
    private fun sessionPolicy() = profiles().let { all ->
        val chosen = graph.config.profileId?.let { id -> all.firstOrNull { it.id == id } }
        (chosen ?: all.firstOrNull { it.location.required } ?: all.firstOrNull())?.location
    }
}
