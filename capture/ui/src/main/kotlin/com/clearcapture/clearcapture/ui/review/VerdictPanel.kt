package com.clearcapture.clearcapture.ui.review

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import com.clearcapture.clearcapture.designsystem.components.CcCheckRow
import com.clearcapture.clearcapture.designsystem.components.CcCheckStatus
import com.clearcapture.clearcapture.designsystem.components.CcPrimaryButton
import com.clearcapture.clearcapture.designsystem.components.CcSecondaryButton
import com.clearcapture.clearcapture.designsystem.components.CcTextField
import com.clearcapture.clearcapture.designsystem.components.CcVerdict
import com.clearcapture.clearcapture.designsystem.components.CcVerdictBadge
import com.clearcapture.clearcapture.designsystem.theme.CcTheme
import com.clearcapture.clearcapture.domain.model.Verdict
import com.clearcapture.clearcapture.domain.usecase.AcceptRules
import com.clearcapture.clearcapture.ui.R
import com.clearcapture.clearcapture.ui.common.BodyText
import com.clearcapture.clearcapture.ui.common.checkName
import com.clearcapture.clearcapture.ui.common.rememberPreview
import com.clearcapture.clearcapture.ui.text.ReasonText

/**
 * The verdict of a photo with a warning or failure, shown on the capture screen (SDK_REQUIREMENTS 7.0): the photo,
 * the verdict, every check with its reason and advice, then Retake and Keep. When Keep is not allowed it is replaced
 * by the reason. Stateless apart from decoding the preview off the main thread.
 */
@Composable
fun VerdictPanel(
    state: ReviewUiState,
    onKeep: () -> Unit,
    onRetake: () -> Unit,
    onReasonChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .background(CcTheme.colors.surface, CcTheme.shapes.large)
            .verticalScroll(rememberScrollState())
            .padding(CcTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(CcTheme.spacing.md),
    ) {
        val preview by rememberPreview(state.imagePath?.let { "file://$it" }, PREVIEW_LONG_SIDE)
        preview?.let {
            Image(
                bitmap = it,
                contentDescription = stringResource(R.string.cc_review_image_description),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = CcTheme.sizes.shutterTarget * PREVIEW_HEIGHT_FACTOR),
            )
        }
        CcVerdictBadge(state.verdict.toCc())
        state.checks.filter { it.verdict != Verdict.PASS }.forEach { row ->
            val texts = row.reason?.let(ReasonText::of)
            CcCheckRow(
                name = checkName(row.check),
                status = row.verdict.toCheck(),
                reason = texts?.let { stringResource(it.first) },
                advice = texts?.let { stringResource(it.second) },
            )
        }
        state.location?.let {
            BodyText(stringResource(R.string.cc_review_location, it.accuracyMeters))
            if (it.isMock) BodyText(stringResource(R.string.cc_review_location_mock))
        }
        if (state.needsOverrideReason) {
            CcTextField(
                value = state.overrideReason,
                onValueChange = onReasonChanged,
                label = stringResource(R.string.cc_review_override_label),
                maxLength = ReviewReducer.MAX_REASON_LENGTH,
                errorMessage = stringResource(R.string.cc_review_override_hint, AcceptRules.MIN_OVERRIDE_REASON_LENGTH)
                    .takeIf { !state.canAccept && state.overrideReason.isNotEmpty() },
            )
        }
        if (state.saveFailed) BodyText(stringResource(R.string.cc_review_save_failed))
        state.acceptBlock?.let { BodyText(blockText(it)) }
        Row(horizontalArrangement = Arrangement.spacedBy(CcTheme.spacing.targetGap)) {
            CcSecondaryButton(
                text = stringResource(R.string.cc_review_retake),
                onClick = onRetake,
                modifier = Modifier.weight(1f),
                enabled = !state.isSaving,
            )
            if (state.acceptBlock == null || state.acceptBlock == AcceptBlock.REASON_TOO_SHORT) {
                CcPrimaryButton(
                    text = stringResource(R.string.cc_session_keep),
                    onClick = onKeep,
                    modifier = Modifier.weight(1f),
                    enabled = state.canAccept,
                    loading = state.isSaving,
                )
            }
        }
    }
}

@Composable
private fun blockText(block: AcceptBlock): String = when (block) {
    AcceptBlock.WARNING_NOT_ALLOWED -> stringResource(R.string.cc_review_block_warning)
    AcceptBlock.FAIL_NOT_ALLOWED -> stringResource(R.string.cc_review_block_fail)
    AcceptBlock.REASON_TOO_SHORT -> stringResource(R.string.cc_review_block_reason, AcceptRules.MIN_OVERRIDE_REASON_LENGTH)
}

private fun VerdictUi.toCc(): CcVerdict = when (this) {
    VerdictUi.Loading -> CcVerdict.Loading
    is VerdictUi.Shown -> when (verdict) {
        Verdict.PASS -> CcVerdict.Pass
        Verdict.WARNING -> CcVerdict.Warning
        Verdict.FAIL -> CcVerdict.Fail
    }
}

private fun Verdict.toCheck(): CcCheckStatus = when (this) {
    Verdict.PASS -> CcCheckStatus.Pass
    Verdict.WARNING -> CcCheckStatus.Warning
    Verdict.FAIL -> CcCheckStatus.Fail
}

private const val PREVIEW_LONG_SIDE = 1024
private const val PREVIEW_HEIGHT_FACTOR = 3
