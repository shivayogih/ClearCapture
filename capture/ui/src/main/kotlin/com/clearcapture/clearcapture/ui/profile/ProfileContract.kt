package com.clearcapture.clearcapture.ui.profile

import com.clearcapture.clearcapture.domain.model.GuideShape

/** A profile as the profile screen shows it. */
data class ProfileUi(val id: String, val titleKey: String, val guideShape: GuideShape)

/** State of the profile screen. */
data class ProfileUiState(
    val isLoading: Boolean = true,
    val profiles: List<ProfileUi> = emptyList(),
    val selectedId: String? = null,
    val isEmpty: Boolean = false,
)

/** Intents of the profile screen. */
sealed interface ProfileIntent {
    /** Load the profiles. */
    data object Load : ProfileIntent

    /** The user picked a profile. */
    data class Select(val id: String) : ProfileIntent

    /** The user left. */
    data object Back : ProfileIntent
}

/** Effects of the profile screen. */
sealed interface ProfileEffect {
    /** Open the camera with the chosen profile. */
    data class NavigateToCamera(val profileId: String) : ProfileEffect

    /** The user left; the flow ends with `Cancelled`. */
    data object Cancelled : ProfileEffect
}
