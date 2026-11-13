package com.clearcapture.clearcapture.domain.port

import com.clearcapture.clearcapture.common.Outcome
import com.clearcapture.clearcapture.domain.model.CameraState
import com.clearcapture.clearcapture.domain.model.CaptureError
import com.clearcapture.clearcapture.domain.model.CaptureProfile
import com.clearcapture.clearcapture.domain.model.CaptureRecord
import com.clearcapture.clearcapture.domain.model.FrameData
import com.clearcapture.clearcapture.domain.model.ImageInfo
import com.clearcapture.clearcapture.domain.model.LocationFix
import com.clearcapture.clearcapture.domain.model.LocationPolicy
import com.clearcapture.clearcapture.domain.model.LocationStatus
import com.clearcapture.clearcapture.domain.model.QualityReport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** The camera, once a screen has bound it. Binding itself lives in `:core:camera`. */
interface CameraController {
    /** Current camera state. */
    val state: StateFlow<CameraState>

    /** Takes a JPEG into [target], passing [location] to be written as EXIF GPS tags. */
    suspend fun takePicture(target: File, location: LocationFix?): Outcome<File, CaptureError>

    /** Focuses and meters at a normalised point of the preview. */
    suspend fun focusAt(x: Float, y: Float)

    /** Switches the torch, when the camera has one. */
    fun setTorch(enabled: Boolean)
}

/** Receives live frames from the camera on the analysis thread. Must return quickly. */
fun interface FrameConsumer {
    /** Called for each analysed frame; [frame] is only valid during this call. */
    fun onFrame(frame: FrameData)
}

/** Image-quality analysis, on live frames and on saved files. */
interface QualityAnalyzer {
    /** Loads the native library. Safe to call more than once. */
    suspend fun warmUp(): Outcome<Unit, CaptureError>

    /** Analyses one live frame synchronously, on the caller's thread. */
    fun analyzeLive(frame: FrameData, profile: CaptureProfile): QualityReport

    /**
     * Analyses a saved photograph. If OpenCV could not be loaded this still returns a report: the
     * resolution and integrity check runs and every OpenCV-based required check is FAIL / ANALYSIS_UNAVAILABLE.
     * [expectedCaptureLongSide] is what the camera reported at bind time, to detect unexpected downscaling.
     */
    suspend fun analyzeFile(
        file: File,
        profile: CaptureProfile,
        expectedCaptureLongSide: Int? = null,
    ): Outcome<QualityReport, CaptureError>
}

/** Location updates for one capture session. */
interface LocationSource {
    /** Status updates; replays the latest status to new collectors. */
    val status: Flow<LocationStatus>

    /** Starts updates under [policy]. */
    fun start(policy: LocationPolicy)

    /** Stops updates. */
    fun stop()

    /** The latest status. */
    fun current(): LocationStatus
}

/** Reads and writes the EXIF tags of a saved photograph. */
interface ExifStore {
    /** Writes every GPS and time tag that is missing, from [fix]. */
    suspend fun writeLocation(file: File, fix: LocationFix): Outcome<Unit, CaptureError>

    /** Reads the GPS tags back, or `null` when latitude or longitude is missing. */
    suspend fun readLocation(file: File): LocationFix?

    /** Copies the GPS and time tags from [from] to [to], after a re-encode. */
    suspend fun copyTags(from: File, to: File)

    /** Dimensions, size, orientation and capture time. */
    suspend fun readImageInfo(file: File): ImageInfo?
}

/** Stores one record per capture attempt. */
interface CaptureRepository {
    /** Inserts or replaces a record. */
    suspend fun saveAttempt(record: CaptureRecord)

    /** Marks a record accepted and stores where the file ended up. */
    suspend fun markAccepted(captureId: String, finalPath: String, overrideReason: String?)

    /** Marks a record discarded; the record is kept for calibration. */
    suspend fun markDiscarded(captureId: String)

    /** Deletes one record. */
    suspend fun delete(captureId: String)

    /** Deletes every record captured before [epochMillis]. */
    suspend fun purgeOlderThan(epochMillis: Long)

    /** All records, newest first. */
    fun observeAll(): Flow<List<CaptureRecord>>
}

/** Supplies capture profiles. */
interface CaptureProfileProvider {
    /** All profiles, in display order. */
    suspend fun profiles(): List<CaptureProfile>

    /** One profile, or `null` when [id] is unknown. */
    suspend fun profile(id: String): CaptureProfile?
}

/** Where photographs are written. */
interface FileStore {
    /** A new file in pending storage for [captureId]. */
    fun newPendingFile(captureId: String): File

    /** Moves a pending file to accepted storage and returns its new location. */
    suspend fun promote(pending: File, captureId: String, epochMillis: Long): Outcome<File, CaptureError>

    /** Deletes a file if it exists. */
    suspend fun delete(file: File)

    /** Free bytes on the volume that holds pending files. */
    fun freeBytes(): Long

    /** Deletes pending files older than [maxAgeMillis]. */
    suspend fun deleteOrphans(maxAgeMillis: Long)
}
