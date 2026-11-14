package com.clearcapture.clearcapture.api.internal.flow

import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import com.clearcapture.clearcapture.api.CaptureConfig
import com.clearcapture.clearcapture.api.CaptureEvent
import com.clearcapture.clearcapture.api.internal.SdkRuntime
import com.clearcapture.clearcapture.camera.CameraXController
import com.clearcapture.clearcapture.common.Clock
import com.clearcapture.clearcapture.common.Outcome
import com.clearcapture.clearcapture.data.file.AppFileStore
import com.clearcapture.clearcapture.domain.model.CameraState
import com.clearcapture.clearcapture.domain.model.CaptureProfile
import com.clearcapture.clearcapture.domain.model.DeviceInfo
import com.clearcapture.clearcapture.domain.model.LocationPolicy
import com.clearcapture.clearcapture.domain.session.CaptureSession
import com.clearcapture.clearcapture.domain.session.LiveAnalysisPipeline
import com.clearcapture.clearcapture.domain.session.PendingCaptures
import com.clearcapture.clearcapture.domain.usecase.AcceptCaptureUseCase
import com.clearcapture.clearcapture.domain.usecase.CaptureEvidenceUseCase
import com.clearcapture.clearcapture.domain.usecase.DiscardCaptureUseCase
import com.clearcapture.clearcapture.domain.usecase.GetProfilesUseCase
import com.clearcapture.clearcapture.domain.usecase.ObserveLiveQualityUseCase
import com.clearcapture.clearcapture.location.AndroidExifStore
import com.clearcapture.clearcapture.location.LocationSources
import com.clearcapture.clearcapture.quality.createQualityAnalyzer
import com.clearcapture.clearcapture.ui.permission.PermissionNames
import com.clearcapture.clearcapture.ui.permission.PermissionSnapshot
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * Objects that live for one run of the capture flow (LLD 10): camera, location, analyzer, live pipeline, session,
 * pending captures and the use cases built from them. Created lazily; closed when the flow ends.
 */
internal class FlowGraph(
    private val runtime: SdkRuntime,
    val config: CaptureConfig,
    private val emit: (CaptureEvent) -> Unit,
) {
    private val context: Context = runtime.appContext
    private val dispatchers = runtime.dispatchers
    val scope = CoroutineScope(SupervisorJob() + dispatchers.main)
    private val clock: Clock = Clock.System

    val camera: CameraXController by lazy { CameraXController(dispatchers.default.asExecutor(), runtime.logger) }
    private val location by lazy { LocationSources.create(context, clock, runtime.logger) }
    private val analyzer by lazy { createQualityAnalyzer(runtime.openCv, dispatchers.default, runtime.logger) }
    val pipeline: LiveAnalysisPipeline by lazy { LiveAnalysisPipeline(analyzer, clock) }
    val session: CaptureSession by lazy { CaptureSession(location, analyzer, scope) }
    val pending = PendingCaptures()

    private val fileStore by lazy {
        AppFileStore(context.cacheDir, context.filesDir, config.outputDirectory?.let(::File), dispatchers.io, clock, runtime.logger)
    }
    private val exifStore by lazy { AndroidExifStore(dispatchers.io, runtime.logger) }

    val getProfiles: GetProfilesUseCase by lazy { GetProfilesUseCase(runtime.profileProvider) }
    val observeGuidance: ObserveLiveQualityUseCase by lazy {
        ObserveLiveQualityUseCase(pipeline.reports, session.analyzerAvailable, location, clock)
    }
    private val captureEvidence by lazy {
        CaptureEvidenceUseCase(
            locationSource = location,
            fileStore = fileStore,
            camera = camera,
            exifStore = exifStore,
            analyzer = analyzer,
            repository = runtime.repository,
            pending = pending,
            clock = clock,
            newId = { UUID.randomUUID().toString() },
            device = {
                val cameraId = (camera.state.value as? CameraState.Ready)?.capabilities?.cameraId.orEmpty()
                DeviceInfo(Build.MODEL, cameraId, Build.VERSION.SDK_INT)
            },
            requestId = config.requestId,
        )
    }
    val accept: AcceptCaptureUseCase by lazy { AcceptCaptureUseCase(fileStore, runtime.repository, pending) }
    val discard: DiscardCaptureUseCase by lazy { DiscardCaptureUseCase(fileStore, runtime.repository, pending) }

    /** Takes a photograph and reports the events the host listens for. */
    suspend fun capture(profile: CaptureProfile, expectedLongSide: Int?) =
        captureEvidence(profile, expectedLongSide).also { outcome ->
            if (outcome is Outcome.Success) {
                emit(CaptureEvent.CaptureTaken(config.requestId, outcome.value.captureId))
                emit(CaptureEvent.Verdict(config.requestId, outcome.value.captureId, outcome.value.report.verdict.name))
            }
        }

    /** Starts location and OpenCV warm-up (LLD 3.2), and clears orphaned pending files. */
    fun startSession(policy: LocationPolicy) {
        session.start(policy)
        scope.launch { fileStore.deleteOrphans(ORPHAN_AGE_MILLIS) }
    }

    /** Reads permission and location-services state for the permission step. */
    fun permissionSnapshot(): PermissionSnapshot {
        fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        val manager = context.getSystemService(LocationManager::class.java)
        return PermissionSnapshot(
            camera = granted(PermissionNames.CAMERA),
            fineLocation = granted(PermissionNames.FINE_LOCATION),
            coarseLocation = granted(PermissionNames.COARSE_LOCATION),
            locationServicesOn = manager?.let(LocationManagerCompat::isLocationEnabled) ?: false,
        )
    }

    /** Ends the flow: stops location, pauses analysis and cancels flow work. */
    fun close() {
        pipeline.setProfile(null)
        session.close()
        scope.cancel()
    }

    private companion object {
        const val ORPHAN_AGE_MILLIS = 24L * 60 * 60 * 1_000
    }
}
