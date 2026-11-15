package com.clearcapture.clearcapture.api

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.clearcapture.clearcapture.api.internal.CaptureActivity
import com.clearcapture.clearcapture.api.internal.mapping.FlowOutcomeMapper
import com.clearcapture.clearcapture.api.internal.readiness.LaunchDecision
import java.io.File

/**
 * Launches the capture flow and returns a [CaptureResult]. Works from any Activity or Fragment, in Java or Kotlin.
 *
 * Failures that need no screen (unknown profile, no camera, unsupported device, low storage, or anything missing in
 * [PermissionMode.HOST_MANAGED]) are returned at once without starting an Activity.
 */
public class EvidenceCaptureContract : ActivityResultContract<CaptureConfig, CaptureResult>() {

    /** @throws IllegalArgumentException when [CaptureConfig.outputDirectory] is outside the app's private storage. */
    override fun createIntent(context: Context, input: CaptureConfig): Intent {
        input.outputDirectory?.let { requireAppPrivate(context, it) }
        return Intent(context, CaptureActivity::class.java).putExtra(CaptureActivity.EXTRA_CONFIG, input)
    }

    override fun getSynchronousResult(context: Context, input: CaptureConfig): SynchronousResult<CaptureResult>? {
        val runtime = ClearCapture.runtime(context)
        val readiness = runtime.readinessChecker.check(input.profileId)
        val failure = LaunchDecision.failureFor(readiness, runtime.config.permissionMode) ?: return null
        return SynchronousResult(FlowOutcomeMapper.map(failure, input.requestId))
    }

    override fun parseResult(resultCode: Int, intent: Intent?): CaptureResult =
        CaptureActivity.readResult(intent).takeIf { resultCode == Activity.RESULT_OK } ?: CaptureResult.Cancelled

    private fun requireAppPrivate(context: Context, path: String) {
        val target = File(path).canonicalFile
        val roots = listOfNotNull(context.filesDir, context.cacheDir, context.getExternalFilesDir(null)).map { it.canonicalFile }
        require(roots.any { root -> target.path == root.path || target.path.startsWith(root.path + File.separator) }) {
            "CaptureConfig.outputDirectory must be inside the app's private storage, was $path"
        }
    }
}
