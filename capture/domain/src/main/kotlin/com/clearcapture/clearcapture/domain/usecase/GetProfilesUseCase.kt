package com.clearcapture.clearcapture.domain.usecase

import com.clearcapture.clearcapture.domain.model.CaptureProfile
import com.clearcapture.clearcapture.domain.port.CaptureProfileProvider

/** Reads the available capture profiles. */
class GetProfilesUseCase(private val provider: CaptureProfileProvider) {
    /** All profiles in display order. */
    suspend operator fun invoke(): List<CaptureProfile> = provider.profiles()

    /** One profile, or `null` if [id] is unknown. */
    suspend fun byId(id: String): CaptureProfile? = provider.profile(id)
}
