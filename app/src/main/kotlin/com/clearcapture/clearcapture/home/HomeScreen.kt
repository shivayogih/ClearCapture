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
import com.clearcapture.clearcapture.api.CapturedImage
import com.clearcapture.clearcapture.api.ClearCapture
import com.clearcapture.clearcapture.api.EvidenceCaptureContract
import com.clearcapture.clearcapture.api.LocationMode
import com.clearcapture.clearcapture.api.Shot
import com.clearcapture.clearcapture.api.StorageTarget
import java.io.File

/** POC home: launch sessions exactly as a host app would, and see readiness and the last result. */
@Composable
internal fun HomeScreen(onOpenGallery: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var lastResult by rememberSaveable { mutableStateOf<String?>(null) }
    var readiness by rememberSaveable { mutableStateOf<String?>(null) }
    val capture = rememberLauncherForActivityResult(EvidenceCaptureContract()) { result -> lastResult = describe(result) }
    val launches = listOf(
        R.string.home_one_container to CaptureConfig(defaultProfileId = "container"),
        R.string.home_planned_three to CaptureConfig(
            shots = listOf(Shot("container", "Bin front"), Shot("container", "Bin side"), Shot("vehicle", "Truck")),
            minPhotos = 3,
            references = mapOf("jobId" to "4711", "binId" to "B-0093"),
        ),
        R.string.home_free_one_to_five to CaptureConfig(minPhotos = 1, maxPhotos = 5),
        R.string.home_location_optional to CaptureConfig(defaultProfileId = "general", locationMode = LocationMode.OPTIONAL, maxPhotos = 2),
        R.string.home_location_off to CaptureConfig(defaultProfileId = "general", locationMode = LocationMode.OFF),
        R.string.home_host_folder to CaptureConfig(
            defaultProfileId = "general",
            storage = StorageTarget.Directory(File(context.getExternalFilesDir(null), "jobs/4711").path),
            allowOverrideOnFail = true,
        ),
        R.string.home_gallery_album to CaptureConfig(defaultProfileId = "general", storage = StorageTarget.SharedPictures("ClearCapture")),
        R.string.home_override to CaptureConfig(defaultProfileId = "general", allowOverrideOnFail = true),
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineSmall)
        launches.forEachIndexed { index, (label, config) ->
            val action = { capture.launch(config) }
            if (index == 0) {
                Button(onClick = action, modifier = Modifier.fillMaxWidth()) { Text(stringResource(label)) }
            } else {
                OutlinedButton(onClick = action, modifier = Modifier.fillMaxWidth()) { Text(stringResource(label)) }
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
    is CaptureResult.Completed -> "Completed with ${result.images.size} photos\n" + describe(result.images)
    is CaptureResult.Cancelled -> "Cancelled with ${result.images.size} kept photos\n" + describe(result.images)
    is CaptureResult.Failed -> "Failed ${result.code}: ${result.message}"
}

private fun describe(images: List<CapturedImage>): String = images.joinToString("\n\n", transform = ::describe)

private fun describe(image: CapturedImage): String = buildString {
    appendLine("${image.label ?: image.profileId}: ${image.quality.verdict} (score ${image.quality.score}) ${image.width}x${image.height}")
    appendLine(image.uri.toString())
    appendLine(image.location?.let { "Location ±${it.accuracyMeters} m, mock=${it.isMock}" } ?: "No location: ${image.locationNote}")
    appendLine("sha256 ${image.sha256.take(16)}…  references ${image.references}")
    image.deliveryNote?.let { appendLine("Delivery: $it") }
    image.quality.checks.filter { it.verdict != "PASS" }.forEach { appendLine("${it.check}: ${it.verdict} ${it.reasonCode.orEmpty()}") }
}
