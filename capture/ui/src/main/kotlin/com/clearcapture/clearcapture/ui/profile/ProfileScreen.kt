package com.clearcapture.clearcapture.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.PaddingValues
import com.clearcapture.clearcapture.designsystem.components.CcProfileCard
import com.clearcapture.clearcapture.designsystem.icons.CcIcons
import com.clearcapture.clearcapture.designsystem.theme.CcTheme
import com.clearcapture.clearcapture.domain.model.GuideShape
import com.clearcapture.clearcapture.ui.R
import com.clearcapture.clearcapture.ui.common.BodyText
import com.clearcapture.clearcapture.ui.common.TitleText
import com.clearcapture.clearcapture.ui.common.profileDescription
import com.clearcapture.clearcapture.ui.common.profileTitle

/** Choose what is being photographed. One column on phones, two on tablets. Stateless. */
@Composable
fun ProfileScreen(state: ProfileUiState, onIntent: (ProfileIntent) -> Unit, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (CcTheme.isTablet) 2 else 1),
        modifier = modifier
            .fillMaxSize()
            .background(CcTheme.colors.surface)
            .safeDrawingPadding(),
        contentPadding = PaddingValues(CcTheme.spacing.screenEdge),
        verticalArrangement = Arrangement.spacedBy(CcTheme.spacing.targetGap),
        horizontalArrangement = Arrangement.spacedBy(CcTheme.spacing.targetGap),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { TitleText(stringResource(R.string.cc_profile_title)) }
        if (state.isEmpty) {
            item(span = { GridItemSpan(maxLineSpan) }) { BodyText(stringResource(R.string.cc_profile_empty)) }
        }
        items(state.profiles, key = { it.id }) { profile ->
            CcProfileCard(
                title = profileTitle(profile.titleKey),
                description = profileDescription(profile.guideShape, profile.id),
                selected = profile.id == state.selectedId,
                onClick = { onIntent(ProfileIntent.Select(profile.id)) },
                icon = when (profile.guideShape) {
                    GuideShape.CONTAINER -> CcIcons.Container
                    GuideShape.PORTRAIT -> CcIcons.Person
                    GuideShape.VEHICLE -> CcIcons.Vehicle
                    GuideShape.NONE -> CcIcons.Camera
                },
            )
        }
    }
}
