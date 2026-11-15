package com.clearcapture.clearcapture.ui.camera

import androidx.lifecycle.viewModelScope
import com.clearcapture.clearcapture.common.Outcome
import com.clearcapture.clearcapture.domain.hint.LiveGuidance
import com.clearcapture.clearcapture.domain.model.CameraState
import com.clearcapture.clearcapture.domain.model.CaptureError
import com.clearcapture.clearcapture.domain.model.CaptureProfile
import com.clearcapture.clearcapture.domain.model.PendingCapture
import com.clearcapture.clearcapture.domain.port.CameraController
import com.clearcapture.clearcapture.domain.usecase.GetProfilesUseCase
import com.clearcapture.clearcapture.ui.mvi.MviViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

/** What the camera screen needs from the rest of the flow, as plain functions so the ViewModel stays testable. */
class CameraDependencies(
    val getProfiles: GetProfilesUseCase,
    val camera: CameraController,
    /** Stable live guidance for a resolved profile. */
    val guidance: (CaptureProfile) -> Flow<LiveGuidance>,
    /** Takes, checks and records a photograph with the resolved profile. */
    val capture: suspend (CaptureProfile, expectedLongSide: Int?) -> Outcome<PendingCapture, CaptureError>,
    /** Called whenever the profile live frames should be analysed against changes. */
    val onActiveProfile: (CaptureProfile?) -> Unit,
    /** Called when the requested profile does not exist. */
    val onProfileMissing: () -> Unit,
)

/** Drives the camera screen. Every state change goes through [CameraReducer]. */
class CameraViewModel(private val deps: CameraDependencies) :
    MviViewModel<CameraUiState, CameraIntent, CameraEffect>(CameraUiState()) {

    private var started: String? = null
    private var guidanceJob: Job? = null
    private var expectedLongSide: Int? = null

    init {
        viewModelScope.launch {
            deps.camera.state.collect { cameraState ->
                when (cameraState) {
                    is CameraState.Ready -> {
                        expectedLongSide = cameraState.capabilities.captureSize?.longSide
                        change(CameraChange.CameraReady(cameraState.capabilities, cameraState.analysisAvailable))
                    }
                    is CameraState.Error -> change(CameraChange.CameraFailed(cameraState.error))
                    CameraState.Idle, CameraState.Starting -> Unit
                }
            }
        }
        // Restart guidance whenever the resolved profile changes (base profile first, then with device overrides).
        viewModelScope.launch {
            state.mapNotNull { it.profile }.distinctUntilChanged().collect { profile ->
                deps.onActiveProfile(profile)
                guidanceJob?.cancel()
                guidanceJob = launch { deps.guidance(profile).collect { change(CameraChange.Guidance(it)) } }
            }
        }
    }

    override fun onIntent(intent: CameraIntent) {
        when (intent) {
            is CameraIntent.Start -> start(intent.profileId)
            CameraIntent.ShutterPressed -> shutter()
            is CameraIntent.TapToFocus -> if (state.value.focusAvailable) viewModelScope.launch { deps.camera.focusAt(intent.x, intent.y) }
            CameraIntent.ToggleTorch -> toggleTorch()
            CameraIntent.BackPressed -> emit(CameraEffect.NavigateBack)
            CameraIntent.OpenLocationSettings -> emit(CameraEffect.OpenLocationSettings)
        }
    }

    override fun onCleared() {
        deps.onActiveProfile(null)
    }

    /** Idempotent, so a recomposition or rotation that re-sends Start does not restart anything. */
    private fun start(profileId: String) {
        if (started == profileId) return
        started = profileId
        viewModelScope.launch {
            val profile = deps.getProfiles.byId(profileId) ?: return@launch deps.onProfileMissing()
            change(CameraChange.ProfileLoaded(profile))
            (deps.camera.state.value as? CameraState.Ready)?.let {
                change(CameraChange.CameraReady(it.capabilities, it.analysisAvailable))
            }
        }
    }

    private fun shutter() {
        val profile = state.value.profile ?: return
        if (!state.value.captureEnabled) return
        emit(CameraEffect.HapticShutter)
        change(CameraChange.CaptureStarted)
        viewModelScope.launch {
            val result = deps.capture(profile, expectedLongSide)
            change(CameraChange.CaptureFinished)
            when (result) {
                is Outcome.Success -> emit(CameraEffect.NavigateToReview(result.value.captureId))
                is Outcome.Failure -> emit(CameraEffect.ShowError(result.error))
            }
        }
    }

    private fun toggleTorch() {
        if (!state.value.torchAvailable) return
        val on = !state.value.torchOn
        deps.camera.setTorch(on)
        change(CameraChange.TorchChanged(on))
    }

    private fun change(change: CameraChange) = setState { CameraReducer.reduce(it, change) }
}
