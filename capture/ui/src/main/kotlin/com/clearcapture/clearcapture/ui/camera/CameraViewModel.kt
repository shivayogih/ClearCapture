package com.clearcapture.clearcapture.ui.camera

import androidx.lifecycle.viewModelScope
import com.clearcapture.clearcapture.domain.hint.LiveGuidance
import com.clearcapture.clearcapture.domain.model.CameraState
import com.clearcapture.clearcapture.domain.port.CameraController
import com.clearcapture.clearcapture.domain.usecase.GetProfilesUseCase
import com.clearcapture.clearcapture.ui.mvi.MviViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Drives the camera screen. [guidance] supplies stable live guidance for a profile; until live validation
 * is connected it may be a constant flow. Every state change goes through [CameraReducer].
 */
class CameraViewModel(
    private val getProfiles: GetProfilesUseCase,
    private val camera: CameraController,
    private val guidance: (profileId: String) -> Flow<LiveGuidance>,
    private val onProfileMissing: () -> Unit,
) : MviViewModel<CameraUiState, CameraIntent, CameraEffect>(CameraUiState()) {

    private var started: String? = null
    private var guidanceJob: Job? = null

    init {
        viewModelScope.launch {
            camera.state.collect { cameraState ->
                when (cameraState) {
                    is CameraState.Ready -> change(CameraChange.CameraReady(cameraState.capabilities, cameraState.analysisAvailable))
                    is CameraState.Error -> change(CameraChange.CameraFailed(cameraState.error))
                    CameraState.Idle, CameraState.Starting -> Unit
                }
            }
        }
    }

    override fun onIntent(intent: CameraIntent) {
        when (intent) {
            is CameraIntent.Start -> start(intent.profileId)
            CameraIntent.ShutterPressed -> if (state.value.captureEnabled) emit(CameraEffect.HapticShutter)
            is CameraIntent.TapToFocus -> if (state.value.focusAvailable) viewModelScope.launch { camera.focusAt(intent.x, intent.y) }
            CameraIntent.ToggleTorch -> if (state.value.torchAvailable) {
                val on = !state.value.torchOn
                camera.setTorch(on)
                change(CameraChange.TorchChanged(on))
            }
            CameraIntent.BackPressed -> emit(CameraEffect.NavigateBack)
            CameraIntent.OpenLocationSettings -> emit(CameraEffect.OpenLocationSettings)
        }
    }

    /** Idempotent, so a recomposition or rotation that re-sends Start does not restart anything. */
    private fun start(profileId: String) {
        if (started == profileId) return
        started = profileId
        viewModelScope.launch {
            val profile = getProfiles.byId(profileId)
            if (profile == null) {
                onProfileMissing()
                return@launch
            }
            change(CameraChange.ProfileLoaded(profile))
            (camera.state.value as? CameraState.Ready)?.let {
                change(CameraChange.CameraReady(it.capabilities, it.analysisAvailable))
            }
            guidanceJob?.cancel()
            guidanceJob = launch { guidance(profileId).collect { change(CameraChange.Guidance(it)) } }
        }
    }

    private fun change(change: CameraChange) = setState { CameraReducer.reduce(it, change) }
}
