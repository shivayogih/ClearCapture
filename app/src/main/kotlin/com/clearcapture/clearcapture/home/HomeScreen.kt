package com.clearcapture.clearcapture.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.clearcapture.clearcapture.R
import com.clearcapture.clearcapture.api.CaptureConfig
import com.clearcapture.clearcapture.api.CaptureResult
import com.clearcapture.clearcapture.api.ClearCapture
import com.clearcapture.clearcapture.api.EvidenceCaptureContract

/** POC home: launch the SDK exactly as a host app would, and see readiness and the last result. */
@Composable
internal fun HomeScreen(onOpenGallery: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var lastResult by rememberSaveable { mutableStateOf<String?>(null) }
    var readiness by rememberSaveable { mutableStateOf<String?>(null) }
    val capture = rememberLauncherForActivityResult(EvidenceCaptureContract()) { result -> lastResult = describe(result) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineSmall)
        Button(onClick = { capture.launch(CaptureConfig()) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.home_capture_choose))
        }
        listOf("container", "person", "vehicle", "general").forEach { id ->
            OutlinedButton(onClick = { capture.launch(CaptureConfig(profileId = id)) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_capture_profile, id))
            }
        }
        OutlinedButton(
            onClick = { readiness = ClearCapture.checkReadiness(context).toString() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.home_check_readiness)) }
        readiness?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        OutlinedButton(onClick = onOpenGallery, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.home_gallery)) }
        lastResult?.let {
            Text(stringResource(R.string.home_last_result), style = MaterialTheme.typography.titleMedium)
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun describe(result: CaptureResult): String = when (result) {
    is CaptureResult.Accepted -> buildString {
        appendLine("Accepted ${result.profileId}: ${result.quality.verdict} (score ${result.quality.score})")
        appendLine(result.imageUri.toString())
        result.location?.let { appendLine("Location ±${it.accuracyMeters} m, mock=${it.isMock}") }
        result.quality.checks.forEach { appendLine("${it.check}: ${it.verdict} ${it.reasonCode.orEmpty()}") }
    }
    CaptureResult.Cancelled -> "Cancelled"
    is CaptureResult.Failed -> "Failed ${result.code}: ${result.message}"
}
