package com.clearcapture.clearcapture.ui.review

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.IOException
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
import com.clearcapture.clearcapture.ui.text.ReasonText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The review screen (DESIGN_SYSTEM 6.1): the photo on the left (landscape) or top (portrait), then the verdict, one
 * row per check, and Retake / Accept with Accept on the thumb side. When Accept is not allowed it is replaced by the
 * reason. Stateless apart from decoding the preview image off the main thread.
 */
@Composable
fun ReviewScreen(state: ReviewUiState, onIntent: (ReviewIntent) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize().background(CcTheme.colors.surface).safeDrawingPadding()) {
        val landscape = maxWidth > maxHeight
        val image: @Composable (Modifier) -> Unit = { m -> ReviewImage(state.imagePath, m) }
        val details: @Composable (Modifier) -> Unit = { m -> ReviewDetails(state, onIntent, m) }
        val content = Modifier.fillMaxSize().padding(CcTheme.spacing.screenEdge)
        if (landscape) {
            Row(content, horizontalArrangement = Arrangement.spacedBy(CcTheme.spacing.xl)) {
                image(Modifier.weight(1f).fillMaxSize())
                details(Modifier.weight(1f).fillMaxSize())
            }
        } else {
            Column(content, verticalArrangement = Arrangement.spacedBy(CcTheme.spacing.lg)) {
                image(Modifier.weight(1f).fillMaxWidth())
                details(Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ReviewImage(path: String?, modifier: Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, path) {
        value = path?.let { withContext(Dispatchers.IO) { decodePreview(it) } }
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = stringResource(R.string.cc_review_image_description),
            contentScale = ContentScale.Fit,
            modifier = modifier,
        )
    }
}

/** Decodes a screen-sized preview, never the full-resolution photo. */
private fun decodePreview(path: String): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= PREVIEW_LONG_SIDE) sample *= 2
    val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    // The JPEG stores sensor-oriented pixels plus an EXIF orientation; turn them upright for display.
    val degrees = try {
        ExifInterface(path).rotationDegrees
    } catch (_: IOException) {
        0
    }
    if (degrees == 0) return bitmap.asImageBitmap()
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).asImageBitmap()
}

@Composable
private fun ReviewDetails(state: ReviewUiState, onIntent: (ReviewIntent) -> Unit, modifier: Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(CcTheme.spacing.md)) {
        CcVerdictBadge(state.verdict.toCc())
        state.checks.forEach { row ->
            val texts = row.reason?.let(ReasonText::of)
            CcCheckRow(
                name = checkName(row.check),
                status = row.verdict.toCheck(),
                reason = texts?.let { stringResource(it.first) } ?: stringResource(R.string.cc_review_check_passed),
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
                onValueChange = { onIntent(ReviewIntent.OverrideReasonChanged(it)) },
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
                onClick = { onIntent(ReviewIntent.Retake) },
                modifier = Modifier.weight(1f),
                enabled = !state.isSaving,
            )
            if (state.acceptBlock == null || state.acceptBlock == AcceptBlock.REASON_TOO_SHORT) {
                CcPrimaryButton(
                    text = stringResource(R.string.cc_review_accept),
                    onClick = { onIntent(ReviewIntent.Accept) },
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

private const val PREVIEW_LONG_SIDE = 1600
