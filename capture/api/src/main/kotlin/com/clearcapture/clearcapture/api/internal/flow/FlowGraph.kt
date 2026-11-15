package com.clearcapture.clearcapture.api.internal.flow

import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.clearcapture.clearcapture.api.CaptureConfig
import com.clearcapture.clearcapture.api.CaptureEvent
import com.clearcapture.clearcapture.api.LocationMode
import com.clearcapture.clearcapture.api.StorageTarget
import com.clearcapture.clearcapture.api.internal.SdkRuntime
import com.clearcapture.clearcapture.camera.CameraXController
import com.clearcapture.clearcapture.common.Clock
import com.clearcapture.clearcapture.common.Outcome
import com.clearcapture.clearcapture.data.destination.DirectoryDestination
import com.clearcapture.clearcapture.data.destination.MediaStoreDestination
import com.clearcapture.clearcapture.data.file.AppFileStore
import com.clearcapture.clearcapture.domain.model.AcceptedCapture
import com.clearcapture.clearcapture.domain.model.CameraState
import com.clearcapture.clearcapture.domain.model.CaptureProfile
import com.clearcapture.clearcapture.domain.model.CaptureStatus
import com.clearcapture.clearcapture.domain.model.DeviceInfo
import com.clearcapture.clearcapture.domain.model.LocationUse
import com.clearcapture.clearcapture.domain.port.ImageDestination
import com.clearcapture.clearcapture.domain.session.CaptureSession
import com.clearcapture.clearcapture.domain.session.LiveAnalysisPipeline
import com.clearcapture.clearcapture.domain.session.PendingCaptures
import com.clearcapture.clearcapture.domain.usecase.CaptureEvidenceUseCase
import com.clearcapture.clearcapture.domain.usecase.DiscardCaptureUseCase
import com.clearcapture.clearcapture.domain.usecase.KeepCaptureUseCase
import com.clearcapture.clearcapture.domain.usecase.ObserveLiveQualityUseCase
import com.clearcapture.clearcapture.location.AndroidExifStore
import com.clearcapture.clearcapture.location.LocationSources
import com.clearcapture.clearcapture.quality.createQualityAnalyzer
import com.clearcapture.clearcapture.ui.permission.PermissionController
import com.clearcapture.clearcapture.ui.permission.PermissionNames
import com.clearcapture.clearcapture.ui.permission.PermissionSnapshot
import com.clearcapture.clearcapture.ui.review.AcceptPolicy
import com.clearcapture.clearcapture.ui.session.CaptureSessionViewModel
import com.clearcapture.clearcapture.ui.session.SessionDependencies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * Objects that live for one capture session (LLD 10): camera, location, analyzer, live pipeline, session, pending
 * photos, storage destinations and the use cases built from them. Created lazily; closed when the session ends.
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
    private val locationUse: LocationUse? = config.locationMode?.let { LocationUse.valueOf(it.name) }

    val camera: CameraXController by lazy { CameraXController(dispatchers.default.asExecutor(), runtime.logger) }
    private val location by lazy { LocationSources.create(context, clock, runtime.logger) }
    private val analyzer by lazy { createQualityAnalyzer(runtime.openCv, dispatchers.default, runtime.logger) }
    val pipeline: LiveAnalysisPipeline by lazy { LiveAnalysisPipeline(analyzer, clock) }
    private val session: CaptureSession by lazy { CaptureSession(location, analyzer, scope) }
    private val pending = PendingCaptures()
    private val fileStore by lazy { AppFileStore(context.cacheDir, dispatchers.io, clock) }
    private val exifStore by lazy { AndroidExifStore(dispatchers.io, runtime.logger) }

    /** The SDK's own storage; also the fallback when the host's destination fails. */
    private val sdkStorage by lazy { DirectoryDestination(File(context.filesDir, "cc_captures"), datedFolders = true, dispatchers.io) }

    /** Where kept photos go, as the host asked (SDK_REQUIREMENTS 7.1). */
    private val destination: ImageDestination by lazy {
        when (val target = config.storage) {
            is StorageTarget.AppPrivate -> target.subdirectory
                ?.let { DirectoryDestination(File(context.filesDir, it), datedFolders = false, dispatchers.io) }
                ?: sdkStorage
            is StorageTarget.Directory -> DirectoryDestination(File(target.path), datedFolders = false, dispatchers.io)
            is StorageTarget.SharedPictures -> MediaStoreDestination(context.contentResolver, target.album, dispatchers.io)
        }
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
    private val keep by lazy {
        KeepCaptureUseCase(destination, sdkStorage, exifStore, runtime.repository, pending, config.references) { sha256(it) }
    }
    private val discard by lazy { DiscardCaptureUseCase(fileStore, runtime.repository, pending) }

    /** Builds the session's ViewModel (the `CaptureViewModelFactory` of LLD 10). */
    fun sessionViewModel(profiles: List<CaptureProfile>): CaptureSessionViewModel {
        val planner = SessionPlanner(config)
        val plan = planner.plan(profiles)
        val permissions = PermissionController(
            locationRequired = planner.locationRequired(profiles, plan),
            locationOptional = config.locationMode == LocationMode.OPTIONAL,
            snapshot = ::permissionSnapshot,
        )
        return CaptureSessionViewModel(
            SessionDependencies(
                plan = plan,
                profiles = { runtime.profileProvider.profiles() },
                camera = camera,
                permissions = permissions,
                guidance = { ObserveLiveQualityUseCase(pipeline.reports, session.analyzerAvailable, location, clock)(it) },
                capture = { profile, longSide, use, label -> capture(profile, longSide, use, label) },
                keep = { capture, policy, reason -> keep(capture, policy.allowAcceptOnWarning, policy.allowOverrideOnFail, reason) },
                discard = { discard(it) },
                removeKept = ::removeKept,
                restoreKept = ::restoreKept,
                acceptPolicy = { profile ->
                    AcceptPolicy(
                        allowAcceptOnWarning = config.allowAcceptOnWarning ?: profile.allowAcceptOnWarning,
                        allowOverrideOnFail = config.allowOverrideOnFail ?: profile.allowOverrideOnFail,
                    )
                },
                freeBytes = fileStore::freeBytes,
                onActiveProfile = pipeline::setProfile,
                onReady = { profile -> profile?.let(::startSession) },
            ),
        )
    }

    private suspend fun capture(profile: CaptureProfile, longSide: Int?, use: LocationUse?, label: String?) =
        captureEvidence(profile, longSide, use, label).also { outcome ->
            if (outcome is Outcome.Success) {
                emit(CaptureEvent.CaptureTaken(config.requestId, outcome.value.captureId))
                emit(CaptureEvent.Verdict(config.requestId, outcome.value.captureId, outcome.value.report.verdict.name))
            }
        }

    /** Starts location (unless off) and OpenCV warm-up (LLD 3.2), and clears orphaned pending files. */
    private fun startSession(profile: CaptureProfile) {
        val policy = (locationUse?.applyTo(profile) ?: profile).location
        session.start(if (config.locationMode == LocationMode.OPTIONAL) policy.copy(required = true) else policy)
        scope.launch { fileStore.deleteOrphans(ORPHAN_AGE_MILLIS) }
    }

    /** Photos kept in this session before the process was killed (SDK_REQUIREMENTS 7.0, interruptions). */
    private suspend fun restoreKept(): List<AcceptedCapture> = runtime.repository.observeAll().first()
        .filter { it.requestId == config.requestId && it.status == CaptureStatus.ACCEPTED && it.report != null }
        .sortedBy { it.capturedAtEpochMillis }
        .map { record ->
            val uri = record.filePath.orEmpty()
            val file = Uri.parse(uri).takeIf { it.scheme == "file" }?.path?.let(::File)
            AcceptedCapture(
                captureId = record.captureId,
                file = file,
                uri = uri,
                profileId = record.profileId,
                report = requireNotNull(record.report),
                overrideReason = record.overrideReason,
                fix = record.fix,
                capturedAtEpochMillis = record.capturedAtEpochMillis,
                sha256 = file?.takeIf(File::exists)?.let { sha256(it) }.orEmpty(),
                width = record.image?.width ?: 0,
                height = record.image?.height ?: 0,
                fileBytes = record.image?.fileBytes ?: 0,
                gpsTime = record.fix != null,
            )
        }

    private suspend fun removeKept(capture: AcceptedCapture) = withContext(dispatchers.io) {
        if (capture.file != null) {
            capture.file?.delete()
        } else {
            runCatching { context.contentResolver.delete(Uri.parse(capture.uri), null, null) }
        }
        runtime.repository.markDiscarded(capture.captureId)
    }

    private suspend fun sha256(file: File): String = withContext(dispatchers.io) {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Reads permission and location-services state for the readiness step. */
    private fun permissionSnapshot(): PermissionSnapshot {
        fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        val manager = context.getSystemService(LocationManager::class.java)
        return PermissionSnapshot(
            camera = granted(PermissionNames.CAMERA),
            fineLocation = granted(PermissionNames.FINE_LOCATION),
            coarseLocation = granted(PermissionNames.COARSE_LOCATION),
            locationServicesOn = manager?.let(LocationManagerCompat::isLocationEnabled) ?: false,
        )
    }

    /** True when the configured destination can be written; checked before the session opens. */
    fun destinationWritable(): Boolean = when (val d = destination) {
        is DirectoryDestination -> d.isWritable()
        else -> true
    }

    /** Ends the session: stops location, pauses analysis and cancels session work. */
    fun close() {
        pipeline.setProfile(null)
        session.close()
        scope.cancel()
    }

    private companion object {
        const val ORPHAN_AGE_MILLIS = 24L * 60 * 60 * 1_000
        const val BUFFER_BYTES = 64 * 1024
    }
}
