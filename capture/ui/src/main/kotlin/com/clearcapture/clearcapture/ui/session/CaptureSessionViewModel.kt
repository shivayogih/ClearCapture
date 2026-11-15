package com.clearcapture.clearcapture.ui.session

import androidx.lifecycle.viewModelScope
import com.clearcapture.clearcapture.common.Outcome
import com.clearcapture.clearcapture.domain.hint.LiveGuidance
import com.clearcapture.clearcapture.domain.model.AcceptedCapture
import com.clearcapture.clearcapture.domain.model.CameraState
import com.clearcapture.clearcapture.domain.model.CaptureError
import com.clearcapture.clearcapture.domain.model.CaptureProfile
import com.clearcapture.clearcapture.domain.model.FailureCode
import com.clearcapture.clearcapture.domain.model.FlowOutcome
import com.clearcapture.clearcapture.domain.model.LocationUse
import com.clearcapture.clearcapture.domain.model.PendingCapture
import com.clearcapture.clearcapture.domain.model.SessionPlan
import com.clearcapture.clearcapture.domain.model.Verdict
import com.clearcapture.clearcapture.domain.port.CameraController
import com.clearcapture.clearcapture.domain.usecase.KeepError
import com.clearcapture.clearcapture.ui.camera.CameraChange
import com.clearcapture.clearcapture.ui.camera.CameraReducer
import com.clearcapture.clearcapture.ui.mvi.MviViewModel
import com.clearcapture.clearcapture.ui.permission.PermissionController
import com.clearcapture.clearcapture.ui.permission.PermissionEffect
import com.clearcapture.clearcapture.ui.permission.PermissionIntent
import com.clearcapture.clearcapture.ui.review.AcceptPolicy
import com.clearcapture.clearcapture.ui.review.ReviewReducer
import com.clearcapture.clearcapture.ui.review.ReviewUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Everything the session needs, as plain functions and ports so the ViewModel is tested without Android. */
@Suppress("LongParameterList") // A dependency holder: one argument per collaborator, all named at the call site.
class SessionDependencies(
    val plan: SessionPlan,
    val profiles: suspend () -> List<CaptureProfile>,
    val camera: CameraController,
    val permissions: PermissionController,
    /** Stable live guidance for a resolved profile. */
    val guidance: (CaptureProfile) -> Flow<LiveGuidance>,
    val capture: suspend (CaptureProfile, expectedLongSide: Int?, LocationUse?, label: String?) -> Outcome<PendingCapture, CaptureError>,
    val keep: suspend (PendingCapture, AcceptPolicy, overrideReason: String?) -> Outcome<AcceptedCapture, KeepError>,
    val discard: suspend (PendingCapture) -> Unit,
    val removeKept: suspend (AcceptedCapture) -> Unit,
    /** Photos kept earlier in this session, after the process was killed and the session reopened. */
    val restoreKept: suspend () -> List<AcceptedCapture>,
    /** The accept rules for a profile, with the host's per-launch overrides applied. */
    val acceptPolicy: (CaptureProfile) -> AcceptPolicy,
    val freeBytes: () -> Long,
    /** Told which profile live frames should be analysed against, or `null` to pause analysis. */
    val onActiveProfile: (CaptureProfile?) -> Unit,
    /** Told once everything needed is granted, so location and OpenCV start warming up. */
    val onReady: (CaptureProfile?) -> Unit,
)

/**
 * The single capture screen of a session (SDK_REQUIREMENTS 7.0): readiness, live guidance, subject per photo,
 * capture, inline verdict, keep or retake, the kept-photo strip, storage alerts and finishing.
 */
@Suppress("TooManyFunctions") // One small handler per intent; splitting them would scatter one screen's rules.
class CaptureSessionViewModel(private val deps: SessionDependencies) :
    MviViewModel<CaptureSessionUiState, SessionIntent, SessionEffect>(
        CaptureSessionUiState(minPhotos = deps.plan.minPhotos, maxPhotos = deps.plan.maxPhotos),
    ) {

    private var profiles: Map<String, CaptureProfile> = emptyMap()
    private val keptCaptures = mutableListOf<AcceptedCapture>()
    private var pending: PendingCapture? = null
    private var expectedLongSide: Int? = null
    private var guidanceJob: Job? = null
    private var chosenSubject: String? = null
    private var finished = false

    init {
        viewModelScope.launch { deps.permissions.state.collect { p -> setState { it.copy(permission = p) } } }
        viewModelScope.launch { deps.permissions.effects.collect(::onPermissionEffect) }
        viewModelScope.launch { observeCamera() }
        viewModelScope.launch { open() }
    }

    override fun onIntent(intent: SessionIntent) {
        when (intent) {
            is SessionIntent.Permission -> onPermissionIntent(intent.intent)
            SessionIntent.Shutter -> shutter()
            is SessionIntent.TapToFocus -> focus(intent.x, intent.y)
            SessionIntent.ToggleTorch -> toggleTorch()
            SessionIntent.OpenLocationSettings -> emit(SessionEffect.OpenLocationSettings)
            is SessionIntent.SelectSubject -> selectSubject(intent.profileId)
            SessionIntent.Keep -> pending?.let { keepPending(it, state.value.panel?.overrideReason) }
            SessionIntent.Retake -> retake()
            is SessionIntent.OverrideReasonChanged -> updatePanelReason(intent.text)
            is SessionIntent.RemovePhoto -> removePhoto(intent.captureId)
            SessionIntent.Finish -> if (state.value.canFinish) finish(FlowOutcome.Completed(keptCaptures.toList()))
            SessionIntent.Leave -> leave()
            SessionIntent.DismissAlert -> setState { it.copy(alert = null) }
            SessionIntent.RetryStore -> retryStore()
            SessionIntent.Resume -> {
                deps.permissions.onIntent(PermissionIntent.Recheck)
                checkStorage()
            }
        }
    }

    private fun onPermissionIntent(intent: PermissionIntent) {
        if (intent == PermissionIntent.Leave) leave() else deps.permissions.onIntent(intent)
    }

    private fun focus(x: Float, y: Float) {
        if (state.value.camera.focusAvailable) viewModelScope.launch { deps.camera.focusAt(x, y) }
    }

    private fun retryStore() {
        val capture = pending ?: return
        setState { it.copy(alert = null) }
        keepPending(capture, state.value.panel?.overrideReason)
    }

    override fun onCleared() {
        deps.onActiveProfile(null)
    }

    private suspend fun open() {
        val all = deps.profiles()
        profiles = all.associateBy { it.id }
        val missing = deps.plan.profileIds.filter { it !in profiles }
        if (missing.isNotEmpty()) {
            finish(FlowOutcome.Failed(FailureCode.PROFILE_NOT_FOUND, "Unknown profiles: ${missing.joinToString(",")}"))
            return
        }
        keptCaptures += deps.restoreKept()
        setState {
            it.copy(
                loading = false,
                subjects = all.map { p -> SubjectOptionUi(p.id, p.titleKey, p.guideShape) },
                canChangeSubject = deps.plan.allowSubjectChange,
                kept = keptCaptures.map(::thumbnail),
            )
        }
        checkStorage()
        applySubject()
        if (deps.permissions.isReady) deps.onReady(currentProfile())
    }

    private suspend fun observeCamera() {
        deps.camera.state.collect { cameraState ->
            when (cameraState) {
                is CameraState.Ready -> {
                    expectedLongSide = cameraState.capabilities.captureSize?.longSide
                    changeCamera(CameraChange.CameraReady(cameraState.capabilities, cameraState.analysisAvailable))
                }
                is CameraState.Error -> changeCamera(CameraChange.CameraFailed(cameraState.error))
                CameraState.Idle, CameraState.Starting -> Unit
            }
        }
    }

    private fun onPermissionEffect(effect: PermissionEffect) {
        when (effect) {
            is PermissionEffect.LaunchRequest -> emit(SessionEffect.LaunchPermissions(effect.kinds, effect.permissions))
            PermissionEffect.OpenAppSettings -> emit(SessionEffect.OpenAppSettings)
            PermissionEffect.OpenLocationSettings -> emit(SessionEffect.OpenLocationSettings)
            PermissionEffect.Granted -> if (!state.value.loading) deps.onReady(currentProfile())
            PermissionEffect.Cancelled -> leave()
        }
    }

    /** The subject of the next photo: the worker's choice, else the plan's shot for this position. */
    private fun currentSubjectId(): String =
        chosenSubject?.takeIf { deps.plan.allowSubjectChange } ?: deps.plan.shotAt(keptCaptures.size).profileId

    private fun currentProfile(): CaptureProfile? = profiles[currentSubjectId()]?.let { base ->
        deps.plan.locationUse?.applyTo(base) ?: base
    }

    private fun applySubject() {
        val profile = currentProfile() ?: return
        setState { it.copy(subjectId = profile.id, label = deps.plan.shotAt(keptCaptures.size).label) }
        changeCamera(CameraChange.ProfileLoaded(profile))
        // Re-resolving with the bound camera's traits applies device overrides to the new subject.
        (deps.camera.state.value as? CameraState.Ready)?.let {
            changeCamera(CameraChange.CameraReady(it.capabilities, it.analysisAvailable))
        }
        restartGuidance()
    }

    /** Guidance follows the resolved profile; it restarts when the subject or the device overrides change it. */
    private fun restartGuidance() {
        guidanceJob?.cancel()
        guidanceJob = viewModelScope.launch {
            state.map { it.camera.profile }.distinctUntilChanged().collect { profile ->
                deps.onActiveProfile(profile)
                if (profile != null) deps.guidance(profile).collect { changeCamera(CameraChange.Guidance(it)) }
            }
        }
    }

    private fun selectSubject(profileId: String) {
        if (!deps.plan.allowSubjectChange || profileId !in profiles || state.value.panel != null) return
        chosenSubject = profileId
        applySubject()
    }

    private fun shutter() {
        val profile = state.value.camera.profile ?: return
        if (!state.value.shutterEnabled) return
        emit(SessionEffect.HapticShutter)
        changeCamera(CameraChange.CaptureStarted)
        setState { it.copy(analysing = true) }
        viewModelScope.launch {
            val result = deps.capture(profile, expectedLongSide, deps.plan.locationUse, state.value.label)
            changeCamera(CameraChange.CaptureFinished)
            setState { it.copy(analysing = false) }
            when (result) {
                is Outcome.Failure -> onCaptureError(result.error)
                is Outcome.Success -> onCaptured(result.value)
            }
        }
    }

    private fun onCaptured(capture: PendingCapture) {
        pending = capture
        emit(SessionEffect.HapticVerdict(capture.report.verdict))
        if (capture.report.verdict == Verdict.PASS) {
            keepPending(capture, null)
        } else {
            val policy = policyFor(capture)
            setState { it.copy(panel = ReviewReducer.loaded(ReviewUiState(), capture, policy)) }
        }
    }

    private fun onCaptureError(error: CaptureError) {
        when (error) {
            // The hint and the location chip already say what to do.
            is CaptureError.LocationBlocked -> Unit
            CaptureError.StorageFull -> setState { it.copy(alert = SessionAlert.StorageFull) }
            else -> setState { it.copy(alert = SessionAlert.CaptureFailed(error)) }
        }
    }

    private fun keepPending(capture: PendingCapture, overrideReason: String?) {
        setState { it.copy(panel = it.panel?.copy(isSaving = true, saveFailed = false)) }
        viewModelScope.launch {
            when (val kept = deps.keep(capture, policyFor(capture), overrideReason)) {
                is Outcome.Success -> onKept(kept.value)
                is Outcome.Failure -> when (val error = kept.error) {
                    KeepError.NotAllowed -> setState { it.copy(panel = it.panel?.copy(isSaving = false)) }
                    is KeepError.Storage -> setState {
                        it.copy(
                            panel = it.panel?.copy(isSaving = false, saveFailed = true),
                            alert = SessionAlert.StoreFailed(error.failure.reason, error.failure.outOfSpace),
                        )
                    }
                }
            }
        }
    }

    private fun onKept(capture: AcceptedCapture) {
        pending = null
        keptCaptures += capture
        chosenSubject = null
        emit(SessionEffect.PhotoKept(keptCaptures.size))
        setState {
            it.copy(
                panel = null,
                kept = keptCaptures.map(::thumbnail),
                alert = capture.deliveryNote?.let { note -> SessionAlert.StoredInFallback(note) } ?: it.alert,
            )
        }
        checkStorage()
        if (keptCaptures.size >= deps.plan.maxPhotos) {
            finish(FlowOutcome.Completed(keptCaptures.toList()))
        } else {
            applySubject()
        }
    }

    private fun retake() {
        val capture = pending ?: return
        pending = null
        setState { it.copy(panel = null, alert = null) }
        viewModelScope.launch { deps.discard(capture) }
    }

    private fun updatePanelReason(text: String) {
        val capture = pending ?: return
        setState { s -> s.copy(panel = s.panel?.let { ReviewReducer.reasonChanged(it, text, capture.report.verdict, policyFor(capture)) }) }
    }

    private fun removePhoto(captureId: String) {
        val capture = keptCaptures.firstOrNull { it.captureId == captureId } ?: return
        if (state.value.panel != null || state.value.analysing) return
        keptCaptures.remove(capture)
        setState { it.copy(kept = keptCaptures.map(::thumbnail)) }
        viewModelScope.launch { deps.removeKept(capture) }
        applySubject()
    }

    private fun toggleTorch() {
        if (!state.value.camera.torchAvailable) return
        val on = !state.value.camera.torchOn
        deps.camera.setTorch(on)
        changeCamera(CameraChange.TorchChanged(on))
    }

    private fun checkStorage() {
        val free = deps.freeBytes()
        setState {
            it.copy(
                storageLow = free < LOW_STORAGE_BYTES,
                alert = when {
                    free < FULL_STORAGE_BYTES -> SessionAlert.StorageFull
                    it.alert == SessionAlert.StorageFull -> null
                    else -> it.alert
                },
            )
        }
    }

    private fun leave() {
        pending?.let { capture -> viewModelScope.launch { deps.discard(capture) } }
        pending = null
        finish(FlowOutcome.Cancelled(keptCaptures.toList()))
    }

    private fun finish(outcome: FlowOutcome) {
        if (finished) return
        finished = true
        emit(SessionEffect.Finish(outcome))
    }

    private fun policyFor(capture: PendingCapture): AcceptPolicy =
        profiles[capture.profileId]?.let(deps.acceptPolicy) ?: AcceptPolicy(allowAcceptOnWarning = true, allowOverrideOnFail = false)

    private fun thumbnail(capture: AcceptedCapture) = KeptPhotoUi(capture.captureId, capture.uri, capture.report.verdict)

    private fun changeCamera(change: CameraChange) = setState { it.copy(camera = CameraReducer.reduce(it.camera, change)) }

    /** Storage limits (SDK_REQUIREMENTS 7.1c). */
    companion object {
        /** Below this the screen warns that photos may not save. */
        const val LOW_STORAGE_BYTES: Long = 150L * 1024 * 1024

        /** Below this the shutter is disabled until space is freed. */
        const val FULL_STORAGE_BYTES: Long = 50L * 1024 * 1024
    }
}
