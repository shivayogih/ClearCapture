package com.clearcapture.clearcapture.ui.permission

/**
 * Pure rules of the readiness permissions (SDK_REQUIREMENTS 5.3, revision 4). No Android types, fully unit-tested.
 *
 * Everything missing is asked for together in one system step; each card can also ask for its own permission.
 * Optional location (LocationMode.OPTIONAL) is asked for once and never blocks capture.
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

    /** Applies the result of one system step that asked for [kinds], given the fresh [snapshot] and the rationale flags. */
    fun onRequestResult(
        state: PermissionUiState,
        kinds: Set<PermissionKind>,
        snapshot: PermissionSnapshot,
        rationale: Map<String, Boolean>,
    ): PermissionUiState = kinds.fold(onSnapshot(state, snapshot)) { acc, kind -> applyResult(state, acc, kind, snapshot, rationale) }

    private fun applyResult(
        before: PermissionUiState,
        refreshed: PermissionUiState,
        kind: PermissionKind,
        snapshot: PermissionSnapshot,
        rationale: Map<String, Boolean>,
    ): PermissionUiState = when (kind) {
        PermissionKind.CAMERA -> if (snapshot.camera) {
            refreshed
        } else {
            val grant = if (rationale[PermissionNames.CAMERA] == true) Grant.DENIED else Grant.PERMANENTLY_DENIED
            refreshed.copy(camera = grant).withStep()
        }
        PermissionKind.LOCATION -> {
            val grant = when {
                refreshed.location == null || snapshot.fineLocation -> null
                // Asked for precise while only approximate was granted, and still only approximate.
                snapshot.coarseLocation && before.location == Grant.APPROXIMATE_ONLY -> Grant.PRECISE_REFUSED
                snapshot.coarseLocation -> Grant.APPROXIMATE_ONLY
                rationale[PermissionNames.FINE_LOCATION] == true || rationale[PermissionNames.COARSE_LOCATION] == true -> Grant.DENIED
                else -> Grant.PERMANENTLY_DENIED
            }
            grant?.let { refreshed.copy(location = it, locationAsked = true).withStep() } ?: refreshed.copy(locationAsked = true).withStep()
        }
    }

    /** The effect of the main button for [state]'s step, or `null` when there is none. */
    fun primaryEffect(state: PermissionUiState): PermissionEffect? = when (state.step) {
        is PermissionStep.Explain, is PermissionStep.Denied, PermissionStep.PreciseNeeded -> requestAllMissing(state)
        is PermissionStep.PermanentlyDenied, PermissionStep.PreciseRefused -> PermissionEffect.OpenAppSettings
        PermissionStep.LocationServicesOff -> PermissionEffect.OpenLocationSettings
        PermissionStep.Done -> PermissionEffect.Granted
    }

    /** The effect of the action on [kind]'s card. */
    fun cardEffect(state: PermissionUiState, kind: PermissionKind): PermissionEffect? {
        val grant = if (kind == PermissionKind.CAMERA) state.camera else state.location
        return when (grant) {
            null, Grant.GRANTED -> null
            Grant.PERMANENTLY_DENIED, Grant.PRECISE_REFUSED -> PermissionEffect.OpenAppSettings
            Grant.NOT_ASKED, Grant.DENIED, Grant.APPROXIMATE_ONLY -> request(setOf(kind))
        }
    }

    /** Computes the single step to show from the grants. */
    fun PermissionUiState.withStep(): PermissionUiState = copy(step = stepFor(this))

    private fun stepFor(state: PermissionUiState): PermissionStep {
        stepFor(PermissionKind.CAMERA, state.camera)?.let { return it }
        val location = state.location ?: return PermissionStep.Done
        if (state.locationOptional) {
            // Optional location is offered once; any answer lets capture continue.
            val unanswered = location == Grant.NOT_ASKED && !state.locationAsked
            return if (unanswered) PermissionStep.Explain(PermissionKind.LOCATION) else PermissionStep.Done
        }
        return stepFor(PermissionKind.LOCATION, location)
            ?: if (!state.locationServicesOn) PermissionStep.LocationServicesOff else PermissionStep.Done
    }

    private fun stepFor(kind: PermissionKind, grant: Grant): PermissionStep? = when (grant) {
        Grant.GRANTED -> null
        Grant.NOT_ASKED -> PermissionStep.Explain(kind)
        Grant.DENIED -> PermissionStep.Denied(kind)
        Grant.PERMANENTLY_DENIED -> PermissionStep.PermanentlyDenied(kind)
        Grant.APPROXIMATE_ONLY -> PermissionStep.PreciseNeeded
        Grant.PRECISE_REFUSED -> PermissionStep.PreciseRefused
    }

    /** Everything still askable in one system step: camera and location together. */
    private fun requestAllMissing(state: PermissionUiState): PermissionEffect.LaunchRequest {
        val askable = setOf(Grant.NOT_ASKED, Grant.DENIED, Grant.APPROXIMATE_ONLY)
        val kinds = buildSet {
            if (state.camera in askable) add(PermissionKind.CAMERA)
            if (state.location in askable && !(state.locationOptional && state.locationAsked)) add(PermissionKind.LOCATION)
        }
        return request(kinds)
    }

    private fun request(kinds: Set<PermissionKind>) =
        PermissionEffect.LaunchRequest(kinds, kinds.sortedBy { it.ordinal }.flatMap { it.manifestPermissions })

    private fun Grant.unlessGranted(): Grant = if (this == Grant.GRANTED) Grant.NOT_ASKED else this
}
