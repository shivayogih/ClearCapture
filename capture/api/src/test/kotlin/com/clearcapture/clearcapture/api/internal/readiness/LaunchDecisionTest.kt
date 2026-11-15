package com.clearcapture.clearcapture.api.internal.readiness

import com.clearcapture.clearcapture.api.Blocker
import com.clearcapture.clearcapture.api.PermissionMode
import com.clearcapture.clearcapture.api.Readiness
import com.clearcapture.clearcapture.domain.model.FailureCode
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LaunchDecisionTest {

    private fun decide(mode: PermissionMode, vararg blockers: Blocker) =
        LaunchDecision.failureFor(Readiness(blockers.toList()), mode)

    @Test
    fun `ready opens the flow in both modes`() {
        assertThat(decide(PermissionMode.SDK_MANAGED)).isNull()
        assertThat(decide(PermissionMode.HOST_MANAGED)).isNull()
    }

    @Test
    fun `sdk managed resolves permission and location blockers with the user`() {
        val outcome = decide(
            PermissionMode.SDK_MANAGED,
            Blocker.CAMERA_PERMISSION_MISSING, Blocker.LOCATION_PRECISE_NOT_GRANTED, Blocker.LOCATION_SERVICES_OFF,
        )
        assertThat(outcome).isNull()
    }

    @Test
    fun `host managed fails with the missing permissions listed`() {
        val outcome = decide(PermissionMode.HOST_MANAGED, Blocker.CAMERA_PERMISSION_MISSING, Blocker.LOCATION_PRECISE_NOT_GRANTED)
        assertThat(outcome?.code).isEqualTo(FailureCode.PERMISSION_MISSING)
        assertThat(outcome?.detail).isEqualTo("CAMERA_PERMISSION_MISSING,LOCATION_PRECISE_NOT_GRANTED")
    }

    @Test
    fun `host managed with location off is not ready`() {
        val outcome = decide(PermissionMode.HOST_MANAGED, Blocker.LOCATION_SERVICES_OFF)
        assertThat(outcome?.code).isEqualTo(FailureCode.NOT_READY)
    }

    @Test
    fun `fatal blockers fail in both modes`() {
        for (mode in PermissionMode.entries) {
            for (blocker in listOf(
                Blocker.NO_CAMERA_HARDWARE, Blocker.CAMERA_DISABLED_BY_POLICY, Blocker.UNSUPPORTED_ABI,
                Blocker.PERMISSION_REMOVED_FROM_MANIFEST, Blocker.NO_LOCATION_PROVIDER,
            )) {
                assertThat(decide(mode, blocker)?.code).isEqualTo(FailureCode.NOT_READY)
            }
        }
    }

    @Test
    fun `unknown profile and low storage have their own codes`() {
        assertThat(decide(PermissionMode.SDK_MANAGED, Blocker.PROFILE_NOT_FOUND)?.code).isEqualTo(FailureCode.PROFILE_NOT_FOUND)
        assertThat(decide(PermissionMode.SDK_MANAGED, Blocker.LOW_STORAGE)?.code).isEqualTo(FailureCode.STORAGE_FULL)
    }

    @Test
    fun `unknown profile wins over other blockers`() {
        val outcome = decide(PermissionMode.HOST_MANAGED, Blocker.CAMERA_PERMISSION_MISSING, Blocker.PROFILE_NOT_FOUND, Blocker.LOW_STORAGE)
        assertThat(outcome?.code).isEqualTo(FailureCode.PROFILE_NOT_FOUND)
    }
}
