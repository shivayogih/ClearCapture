package com.clearcapture.clearcapture.ui.permission

/**
 * Pure rules of the permission step (SDK_REQUIREMENTS section 5.3). No Android types, fully unit-tested.
 * Camera is asked first, then location, then location services.
 */
object PermissionReducer {

    /** Merges a fresh [snapshot] into [state], keeping what the flow learnt from earlier refusals. */
    fun onSnapshot(state: PermissionUiState, snapshot: PermissionSnapshot): PermissionUiState {
        val camera = if (snapshot.camera) Grant.GRANTED else state.camera.unlessGranted()
        val location = state.location?.let { previous ->
            when {
                snapshot.fineLocation -> Grant.GRANTED
                snapshot.coarseLocation -> if (previous == Grant.PRECISE_REFUSED) Grant.PRECISE_REFUSED else Grant.APPROXIMATE_ONLY
                else -> previous.unlessGranted()
            }
        }
        return state.copy(camera = camera, location = location, locationServicesOn = snapshot.locationServicesOn).withStep()
    }

    /** Applies the result of a system dialog for [kind], given the fresh [snapshot] and the rationale flags. */
    fun onRequestResult(
        state: PermissionUiState,
        kind: PermissionKind,
        snapshot: PermissionSnapshot,
        rationale: Map<String, Boolean>,
    ): PermissionUiState {
        val refreshed = onSnapshot(state, snapshot)
        return when (kind) {
            PermissionKind.CAMERA -> {
                if (snapshot.camera) return refreshed
                val grant = if (rationale[PermissionNames.CAMERA] == true) Grant.DENIED else Grant.PERMANENTLY_DENIED
                refreshed.copy(camera = grant).withStep()
            }
            PermissionKind.LOCATION -> {
                val grant = when {
                    refreshed.location == null || snapshot.fineLocation -> return refreshed
                    // Asked for precise while only approximate was granted, and still only approximate.
                    snapshot.coarseLocation && state.location == Grant.APPROXIMATE_ONLY -> Grant.PRECISE_REFUSED
                    snapshot.coarseLocation -> Grant.APPROXIMATE_ONLY
                    rationale[PermissionNames.FINE_LOCATION] == true || rationale[PermissionNames.COARSE_LOCATION] == true ->
                        Grant.DENIED
                    else -> Grant.PERMANENTLY_DENIED
                }
                refreshed.copy(location = grant).withStep()
            }
        }
    }

    /** The effect of the primary button in [state]'s step, or `null` when there is none. */
    fun primaryEffect(state: PermissionUiState): PermissionEffect? = when (val step = state.step) {
        is PermissionStep.Explain -> request(step.kind)
        is PermissionStep.Denied -> request(step.kind)
        PermissionStep.PreciseNeeded -> request(PermissionKind.LOCATION)
        is PermissionStep.PermanentlyDenied, PermissionStep.PreciseRefused -> PermissionEffect.OpenAppSettings
        PermissionStep.LocationServicesOff -> PermissionEffect.OpenLocationSettings
        PermissionStep.Done -> PermissionEffect.Granted
    }

    /** Computes the single step to show from the grants. */
    fun PermissionUiState.withStep(): PermissionUiState = copy(step = stepFor(this))

    private fun stepFor(state: PermissionUiState): PermissionStep =
        stepFor(PermissionKind.CAMERA, state.camera)
            ?: state.location?.let { stepFor(PermissionKind.LOCATION, it) }
            ?: if (state.location != null && !state.locationServicesOn) PermissionStep.LocationServicesOff else PermissionStep.Done

    private fun stepFor(kind: PermissionKind, grant: Grant): PermissionStep? = when (grant) {
        Grant.GRANTED -> null
        Grant.NOT_ASKED -> PermissionStep.Explain(kind)
        Grant.DENIED -> PermissionStep.Denied(kind)
        Grant.PERMANENTLY_DENIED -> PermissionStep.PermanentlyDenied(kind)
        Grant.APPROXIMATE_ONLY -> PermissionStep.PreciseNeeded
        Grant.PRECISE_REFUSED -> PermissionStep.PreciseRefused
    }

    private fun request(kind: PermissionKind) = PermissionEffect.LaunchRequest(kind, kind.manifestPermissions)

    private fun Grant.unlessGranted(): Grant = if (this == Grant.GRANTED) Grant.NOT_ASKED else this
}
