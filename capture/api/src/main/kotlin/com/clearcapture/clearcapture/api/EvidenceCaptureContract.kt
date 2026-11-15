package com.clearcapture.clearcapture.api

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.clearcapture.clearcapture.api.internal.CaptureActivity
import com.clearcapture.clearcapture.api.internal.flow.SessionPlanner
import com.clearcapture.clearcapture.api.internal.mapping.FlowOutcomeMapper
import com.clearcapture.clearcapture.api.internal.readiness.LaunchDecision
import com.clearcapture.clearcapture.domain.model.FailureCode
import com.clearcapture.clearcapture.domain.model.FlowOutcome
import java.io.File

/**
 * Launches a capture session and returns a [CaptureResult]. Works from any Activity or Fragment, in Java or Kotlin.
 *
 * Failures that need no screen are returned at once without starting an Activity: an unknown subject profile, no
 * camera, an unsupported device, low storage, a storage destination that cannot be written, or anything missing in
 * [PermissionMode.HOST_MANAGED].
 */
public class EvidenceCaptureContract : ActivityResultContract<CaptureConfig, CaptureResult>() {

    override fun createIntent(context: Context, input: CaptureConfig): Intent =
        Intent(context, CaptureActivity::class.java).putExtra(CaptureActivity.EXTRA_CONFIG, input)

    override fun getSynchronousResult(context: Context, input: CaptureConfig): SynchronousResult<CaptureResult>? {
        val runtime = ClearCapture.runtime(context)
        val profiles = runtime.profilesBlocking()
        val planner = SessionPlanner(input)
        val plan = planner.plan(profiles)
        val readiness = runtime.readinessChecker.check(plan.profileIds, planner.locationRequired(profiles, plan))
        val failure = LaunchDecision.failureFor(readiness, runtime.config.permissionMode)
            ?: unwritable(input.storage)?.let { FlowOutcome.Failed(FailureCode.STORAGE_UNAVAILABLE, it) }
            ?: return null
        return SynchronousResult(FlowOutcomeMapper.map(failure, input.requestId, input.references))
    }

    override fun parseResult(resultCode: Int, intent: Intent?): CaptureResult =
        CaptureActivity.readResult(intent).takeIf { resultCode == Activity.RESULT_OK } ?: CaptureResult.Cancelled()

    /** Why the host's folder cannot be used, or `null` when it can (created now if missing). */
    private fun unwritable(storage: StorageTarget): String? {
        val folder = (storage as? StorageTarget.Directory)?.path?.let(::File) ?: return null
        val usable = try {
            (folder.isDirectory || folder.mkdirs()) && folder.canWrite()
        } catch (_: SecurityException) {
            false
        }
        return if (usable) null else "The folder ${folder.path} cannot be written by this app"
    }
}
