package com.clearcapture.clearcapture.api.internal.readiness

import com.clearcapture.clearcapture.api.Blocker
import com.clearcapture.clearcapture.api.PermissionMode
import com.clearcapture.clearcapture.api.Readiness
import com.clearcapture.clearcapture.domain.model.FailureCode
import com.clearcapture.clearcapture.domain.model.FlowOutcome

/** Decides, before any screen is shown, whether a launch must fail at once (LLD section 2 rules). */
internal object LaunchDecision {

    private val permissionBlockers = setOf(
        Blocker.CAMERA_PERMISSION_MISSING,
        Blocker.LOCATION_PERMISSION_MISSING,
        Blocker.LOCATION_PRECISE_NOT_GRANTED,
    )

    /** The failure to return immediately, or `null` when the flow may open. */
    fun failureFor(readiness: Readiness, mode: PermissionMode): FlowOutcome.Failed? {
        val blockers = readiness.blockers
        val fatalNotReady = blockers.filter { it.isFatal && it != Blocker.PROFILE_NOT_FOUND && it != Blocker.LOW_STORAGE }
        return when {
            Blocker.PROFILE_NOT_FOUND in blockers -> failed(FailureCode.PROFILE_NOT_FOUND, listOf(Blocker.PROFILE_NOT_FOUND))
            fatalNotReady.isNotEmpty() -> failed(FailureCode.NOT_READY, fatalNotReady)
            Blocker.LOW_STORAGE in blockers -> failed(FailureCode.STORAGE_FULL, listOf(Blocker.LOW_STORAGE))
            mode == PermissionMode.HOST_MANAGED && blockers.any { it in permissionBlockers } ->
                failed(FailureCode.PERMISSION_MISSING, blockers.filter { it in permissionBlockers })
            // In host-managed mode the SDK shows no screen on which the user could turn location on.
            mode == PermissionMode.HOST_MANAGED && Blocker.LOCATION_SERVICES_OFF in blockers ->
                failed(FailureCode.NOT_READY, listOf(Blocker.LOCATION_SERVICES_OFF))
            else -> null
        }
    }

    private fun failed(code: FailureCode, blockers: List<Blocker>) =
        FlowOutcome.Failed(code, blockers.joinToString(",") { it.name })
}
