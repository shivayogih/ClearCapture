package com.clearcapture.clearcapture.ui.permission

import app.cash.turbine.test
import com.clearcapture.clearcapture.ui.MainDispatcherRule
import com.clearcapture.clearcapture.ui.permission.PermissionNames.CAMERA
import com.clearcapture.clearcapture.ui.permission.PermissionNames.COARSE_LOCATION
import com.clearcapture.clearcapture.ui.permission.PermissionNames.FINE_LOCATION
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** One test per row of SDK_REQUIREMENTS section 5.3 that the permission step owns. */
class PermissionViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private var system = PermissionSnapshot(camera = false, fineLocation = false, coarseLocation = false, locationServicesOn = true)

    private fun viewModel(locationRequired: Boolean = true) = PermissionViewModel(locationRequired) { system }

    private val noRationale = emptyMap<String, Boolean>()

    @Test
    fun `first request explains camera first, then location`() = runTest {
        val vm = viewModel()
        assertThat(vm.state.value.step).isEqualTo(PermissionStep.Explain(PermissionKind.CAMERA))
        vm.effects.test {
            vm.onIntent(PermissionIntent.PrimaryAction)
            assertThat(awaitItem()).isEqualTo(PermissionEffect.LaunchRequest(PermissionKind.CAMERA, listOf(CAMERA)))

            system = system.copy(camera = true)
            vm.onIntent(PermissionIntent.RequestResult(PermissionKind.CAMERA, noRationale))
            assertThat(vm.state.value.step).isEqualTo(PermissionStep.Explain(PermissionKind.LOCATION))

            vm.onIntent(PermissionIntent.PrimaryAction)
            assertThat(awaitItem())
                .isEqualTo(PermissionEffect.LaunchRequest(PermissionKind.LOCATION, listOf(FINE_LOCATION, COARSE_LOCATION)))
        }
    }

    @Test
    fun `denied once explains and offers to ask again`() = runTest {
        val vm = viewModel()
        vm.onIntent(PermissionIntent.RequestResult(PermissionKind.CAMERA, mapOf(CAMERA to true)))
        assertThat(vm.state.value.step).isEqualTo(PermissionStep.Denied(PermissionKind.CAMERA))
        vm.effects.test {
            vm.onIntent(PermissionIntent.PrimaryAction)
            assertThat(awaitItem()).isInstanceOf(PermissionEffect.LaunchRequest::class.java)
        }
    }

    @Test
    fun `permanently denied offers settings and rechecks on return`() = runTest {
        val vm = viewModel(locationRequired = false)
        vm.onIntent(PermissionIntent.RequestResult(PermissionKind.CAMERA, mapOf(CAMERA to false)))
        assertThat(vm.state.value.step).isEqualTo(PermissionStep.PermanentlyDenied(PermissionKind.CAMERA))
        vm.effects.test {
            vm.onIntent(PermissionIntent.PrimaryAction)
            assertThat(awaitItem()).isEqualTo(PermissionEffect.OpenAppSettings)

            system = system.copy(camera = true)
            vm.onIntent(PermissionIntent.Recheck)
            assertThat(awaitItem()).isEqualTo(PermissionEffect.Granted)
        }
    }

    @Test
    fun `approximate only asks for precise, and a refusal routes to settings`() = runTest {
        system = system.copy(camera = true)
        val vm = viewModel()
        system = system.copy(coarseLocation = true)
        vm.onIntent(PermissionIntent.RequestResult(PermissionKind.LOCATION, noRationale))
        assertThat(vm.state.value.step).isEqualTo(PermissionStep.PreciseNeeded)

        vm.onIntent(PermissionIntent.RequestResult(PermissionKind.LOCATION, noRationale))
        assertThat(vm.state.value.step).isEqualTo(PermissionStep.PreciseRefused)
        vm.effects.test {
            vm.onIntent(PermissionIntent.PrimaryAction)
            assertThat(awaitItem()).isEqualTo(PermissionEffect.OpenAppSettings)
        }
    }

    @Test
    fun `approximate upgraded to precise completes`() = runTest {
        system = system.copy(camera = true, coarseLocation = true)
        val vm = viewModel()
        assertThat(vm.state.value.step).isEqualTo(PermissionStep.PreciseNeeded)
        vm.effects.test {
            system = system.copy(fineLocation = true)
            vm.onIntent(PermissionIntent.RequestResult(PermissionKind.LOCATION, noRationale))
            assertThat(awaitItem()).isEqualTo(PermissionEffect.Granted)
        }
    }

    @Test
    fun `one-time grant counts as granted for this run`() = runTest {
        system = system.copy(camera = true, fineLocation = true, coarseLocation = true)
        val vm = viewModel()
        assertThat(vm.state.value.step).isEqualTo(PermissionStep.Done)
    }

    @Test
    fun `location services off routes to location settings and rechecks`() = runTest {
        system = PermissionSnapshot(camera = true, fineLocation = true, coarseLocation = true, locationServicesOn = false)
        val vm = viewModel()
        assertThat(vm.state.value.step).isEqualTo(PermissionStep.LocationServicesOff)
        vm.effects.test {
            vm.onIntent(PermissionIntent.PrimaryAction)
            assertThat(awaitItem()).isEqualTo(PermissionEffect.OpenLocationSettings)
            system = system.copy(locationServicesOn = true)
            vm.onIntent(PermissionIntent.Recheck)
            assertThat(awaitItem()).isEqualTo(PermissionEffect.Granted)
        }
    }

    @Test
    fun `profile without location never asks for it, even with location off`() = runTest {
        system = PermissionSnapshot(camera = true, fineLocation = false, coarseLocation = false, locationServicesOn = false)
        val vm = viewModel(locationRequired = false)
        assertThat(vm.state.value.location).isNull()
        assertThat(vm.state.value.step).isEqualTo(PermissionStep.Done)
    }

    @Test
    fun `already granted shows nothing and reports granted once`() = runTest {
        system = PermissionSnapshot(camera = true, fineLocation = true, coarseLocation = true, locationServicesOn = true)
        val vm = viewModel()
        vm.effects.test {
            assertThat(awaitItem()).isEqualTo(PermissionEffect.Granted)
            vm.onIntent(PermissionIntent.Recheck)
            expectNoEvents()
        }
    }

    @Test
    fun `location refused permanently routes to settings`() = runTest {
        system = system.copy(camera = true)
        val vm = viewModel()
        vm.onIntent(PermissionIntent.RequestResult(PermissionKind.LOCATION, mapOf(FINE_LOCATION to false, COARSE_LOCATION to false)))
        assertThat(vm.state.value.step).isEqualTo(PermissionStep.PermanentlyDenied(PermissionKind.LOCATION))
    }

    @Test
    fun `permission revoked in settings is asked again`() = runTest {
        system = PermissionSnapshot(camera = true, fineLocation = true, coarseLocation = true, locationServicesOn = true)
        val vm = viewModel()
        system = system.copy(camera = false)
        vm.onIntent(PermissionIntent.Recheck)
        assertThat(vm.state.value.step).isEqualTo(PermissionStep.Explain(PermissionKind.CAMERA))
    }

    @Test
    fun `each card asks for its own permission, out of order if the user chooses`() = runTest {
        val vm = viewModel()
        vm.effects.test {
            vm.onIntent(PermissionIntent.CardAction(PermissionKind.LOCATION))
            assertThat(awaitItem()).isEqualTo(PermissionEffect.LaunchRequest(PermissionKind.LOCATION, listOf(FINE_LOCATION, COARSE_LOCATION)))
            vm.onIntent(PermissionIntent.CardAction(PermissionKind.CAMERA))
            assertThat(awaitItem()).isEqualTo(PermissionEffect.LaunchRequest(PermissionKind.CAMERA, listOf(CAMERA)))
        }
    }

    @Test
    fun `a permanently denied card opens settings and a granted card does nothing`() = runTest {
        system = system.copy(fineLocation = true, coarseLocation = true)
        val vm = viewModel()
        vm.onIntent(PermissionIntent.RequestResult(PermissionKind.CAMERA, mapOf(CAMERA to false)))
        vm.effects.test {
            vm.onIntent(PermissionIntent.CardAction(PermissionKind.CAMERA))
            assertThat(awaitItem()).isEqualTo(PermissionEffect.OpenAppSettings)
            vm.onIntent(PermissionIntent.CardAction(PermissionKind.LOCATION))
            expectNoEvents()
        }
    }

    @Test
    fun `leaving cancels`() = runTest {
        val vm = viewModel()
        vm.effects.test {
            vm.onIntent(PermissionIntent.Leave)
            assertThat(awaitItem()).isEqualTo(PermissionEffect.Cancelled)
        }
    }
}
