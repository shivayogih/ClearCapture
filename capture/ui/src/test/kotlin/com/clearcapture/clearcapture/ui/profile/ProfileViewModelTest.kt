package com.clearcapture.clearcapture.ui.profile

import app.cash.turbine.test
import com.clearcapture.clearcapture.domain.model.CaptureProfile
import com.clearcapture.clearcapture.domain.model.TestProfiles
import com.clearcapture.clearcapture.domain.port.CaptureProfileProvider
import com.clearcapture.clearcapture.domain.usecase.GetProfilesUseCase
import com.clearcapture.clearcapture.ui.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class ProfileViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private fun provider(list: List<CaptureProfile>) = object : CaptureProfileProvider {
        override suspend fun profiles() = list
        override suspend fun profile(id: String) = list.firstOrNull { it.id == id }
    }

    @Test
    fun `loads profiles in order`() {
        val vm = ProfileViewModel(GetProfilesUseCase(provider(listOf(TestProfiles.container(), TestProfiles.general()))))
        assertThat(vm.state.value.isLoading).isFalse()
        assertThat(vm.state.value.profiles.map { it.id }).containsExactly("container", "general").inOrder()
    }

    @Test
    fun `empty list is reported`() {
        val vm = ProfileViewModel(GetProfilesUseCase(provider(emptyList())))
        assertThat(vm.state.value.isEmpty).isTrue()
    }

    @Test
    fun `selecting navigates to the camera`() = runTest {
        val vm = ProfileViewModel(GetProfilesUseCase(provider(listOf(TestProfiles.container()))))
        vm.effects.test {
            vm.onIntent(ProfileIntent.Select("container"))
            assertThat(awaitItem()).isEqualTo(ProfileEffect.NavigateToCamera("container"))
            assertThat(vm.state.value.selectedId).isEqualTo("container")
        }
    }

    @Test
    fun `back cancels`() = runTest {
        val vm = ProfileViewModel(GetProfilesUseCase(provider(emptyList())))
        vm.effects.test {
            vm.onIntent(ProfileIntent.Back)
            assertThat(awaitItem()).isEqualTo(ProfileEffect.Cancelled)
        }
    }
}
