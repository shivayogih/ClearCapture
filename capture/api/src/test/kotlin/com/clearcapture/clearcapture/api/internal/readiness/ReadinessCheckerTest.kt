package com.clearcapture.clearcapture.api.internal.readiness

import com.clearcapture.clearcapture.api.Blocker
import com.clearcapture.clearcapture.api.internal.readiness.ReadinessChecker.Companion.CAMERA
import com.clearcapture.clearcapture.api.internal.readiness.ReadinessChecker.Companion.COARSE_LOCATION
import com.clearcapture.clearcapture.api.internal.readiness.ReadinessChecker.Companion.FINE_LOCATION
import com.clearcapture.clearcapture.api.internal.readiness.ReadinessChecker.Companion.SDK_PERMISSIONS
import com.clearcapture.clearcapture.domain.model.TestProfiles
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReadinessCheckerTest {

    private class FakeEnvironment(
        var declared: Set<String> = SDK_PERMISSIONS,
        var granted: MutableSet<String> = SDK_PERMISSIONS.toMutableSet(),
        var backCamera: Boolean = true,
        var policyDisabled: Boolean = false,
        var provider: Boolean = true,
        var locationOn: Boolean = true,
        var free: Long = 1L shl 32,
        var nativeLib: Boolean = true,
    ) : ReadinessEnvironment {
        var reads = 0
        override fun declaredPermissions() = declared.also { reads++ }
        override fun isGranted(permission: String) = permission in granted
        override fun hasBackCamera() = backCamera
        override fun cameraDisabledByPolicy() = policyDisabled
        override fun hasLocationProvider() = provider
        override fun locationEnabled() = locationOn
        override fun freeBytes() = free
        override fun nativeLibraryAvailable() = nativeLib
    }

    private val env = FakeEnvironment()
    private val locationFree = TestProfiles.general().copy(
        id = "nolocation",
        location = TestProfiles.defaultLocation.copy(required = false),
    )
    private var profiles = listOf(TestProfiles.container(), TestProfiles.general())
    private val checker = ReadinessChecker(env) { profiles }

    @Test
    fun `everything in place is ready`() {
        val readiness = checker.check("container")
        assertThat(readiness.isReady).isTrue()
        assertThat(readiness.blockers).isEmpty()
    }

    @Test
    fun `no back camera`() {
        env.backCamera = false
        assertThat(checker.check(null).blockers).containsExactly(Blocker.NO_CAMERA_HARDWARE)
    }

    @Test
    fun `camera disabled by policy`() {
        env.policyDisabled = true
        assertThat(checker.check(null).blockers).containsExactly(Blocker.CAMERA_DISABLED_BY_POLICY)
    }

    @Test
    fun `camera permission missing`() {
        env.granted.remove(CAMERA)
        assertThat(checker.check(null).blockers).containsExactly(Blocker.CAMERA_PERMISSION_MISSING)
    }

    @Test
    fun `location permission missing`() {
        env.granted.removeAll(setOf(FINE_LOCATION, COARSE_LOCATION))
        assertThat(checker.check("container").blockers).containsExactly(Blocker.LOCATION_PERMISSION_MISSING)
    }

    @Test
    fun `approximate location only`() {
        env.granted.remove(FINE_LOCATION)
        assertThat(checker.check("container").blockers).containsExactly(Blocker.LOCATION_PRECISE_NOT_GRANTED)
    }

    @Test
    fun `location services off`() {
        env.locationOn = false
        assertThat(checker.check("container").blockers).containsExactly(Blocker.LOCATION_SERVICES_OFF)
    }

    @Test
    fun `no location provider`() {
        env.provider = false
        assertThat(checker.check("container").blockers).containsExactly(Blocker.NO_LOCATION_PROVIDER)
    }

    @Test
    fun `low storage`() {
        env.free = ReadinessChecker.MIN_FREE_BYTES - 1
        assertThat(checker.check(null).blockers).containsExactly(Blocker.LOW_STORAGE)
    }

    @Test
    fun `unsupported abi`() {
        env.nativeLib = false
        assertThat(checker.check(null).blockers).containsExactly(Blocker.UNSUPPORTED_ABI)
    }

    @Test
    fun `unknown profile`() {
        assertThat(checker.check("spaceship").blockers).contains(Blocker.PROFILE_NOT_FOUND)
    }

    @Test
    fun `permission removed from merged manifest`() {
        env.declared = setOf(CAMERA, COARSE_LOCATION)
        env.granted.remove(FINE_LOCATION)
        assertThat(checker.check("container").blockers)
            .containsExactly(Blocker.PERMISSION_REMOVED_FROM_MANIFEST, Blocker.LOCATION_PRECISE_NOT_GRANTED)
    }

    @Test
    fun `profile that needs no location ignores location state`() {
        profiles = listOf(locationFree)
        env.granted.removeAll(setOf(FINE_LOCATION, COARSE_LOCATION))
        env.locationOn = false
        assertThat(checker.check("nolocation").isReady).isTrue()
    }

    @Test
    fun `without a profile id location is needed when any profile needs it`() {
        profiles = listOf(locationFree, TestProfiles.container())
        env.granted.removeAll(setOf(FINE_LOCATION, COARSE_LOCATION))
        assertThat(checker.check(null).blockers).containsExactly(Blocker.LOCATION_PERMISSION_MISSING)
    }

    @Test
    fun `blockers are reported in a stable order`() {
        env.free = 0
        env.granted.clear()
        env.backCamera = false
        assertThat(checker.check("container").blockers).isInOrder()
    }
}
