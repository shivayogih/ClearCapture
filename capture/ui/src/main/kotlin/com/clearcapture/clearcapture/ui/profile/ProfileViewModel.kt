package com.clearcapture.clearcapture.ui.profile

import androidx.lifecycle.viewModelScope
import com.clearcapture.clearcapture.domain.usecase.GetProfilesUseCase
import com.clearcapture.clearcapture.ui.mvi.MviViewModel
import kotlinx.coroutines.launch

/** Lists the profiles and reports the user's choice. */
class ProfileViewModel(
    private val getProfiles: GetProfilesUseCase,
) : MviViewModel<ProfileUiState, ProfileIntent, ProfileEffect>(ProfileUiState()) {

    init {
        onIntent(ProfileIntent.Load)
    }

    override fun onIntent(intent: ProfileIntent) {
        when (intent) {
            ProfileIntent.Load -> viewModelScope.launch {
                val profiles = getProfiles().map { ProfileUi(it.id, it.titleKey, it.guideShape) }
                setState { it.copy(isLoading = false, profiles = profiles, isEmpty = profiles.isEmpty()) }
            }
            is ProfileIntent.Select -> {
                setState { it.copy(selectedId = intent.id) }
                emit(ProfileEffect.NavigateToCamera(intent.id))
            }
            ProfileIntent.Back -> emit(ProfileEffect.Cancelled)
        }
    }
}
