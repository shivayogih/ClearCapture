package com.clearcapture.clearcapture.data.record

import com.clearcapture.clearcapture.domain.model.CaptureRecord
import com.clearcapture.clearcapture.domain.model.CaptureStatus
import com.clearcapture.clearcapture.domain.model.CheckResult
import com.clearcapture.clearcapture.domain.model.DeviceInfo
import com.clearcapture.clearcapture.domain.model.ImageInfo
import com.clearcapture.clearcapture.domain.model.LocationFix
import com.clearcapture.clearcapture.domain.model.QualityCheck
import com.clearcapture.clearcapture.domain.model.QualityReport
import com.clearcapture.clearcapture.domain.model.ReasonCode
import com.clearcapture.clearcapture.domain.model.Stage
import com.clearcapture.clearcapture.domain.model.Verdict
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Maps between domain records and Room rows. Check results are stored as JSON so new metrics need no migration. */
internal object RecordMapper {

    @Serializable
    private data class CheckJson(
        val check: String,
        val verdict: String,
        val reason: String? = null,
        val metrics: Map<String, Double> = emptyMap(),
        val thresholds: Map<String, Double> = emptyMap(),
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(CheckJson.serializer())

    fun toEntity(record: CaptureRecord): CaptureEntity = CaptureEntity(
        id = record.captureId,
        requestId = record.requestId,
        profileId = record.profileId,
        status = record.status.name,
        verdict = record.report?.verdict?.name,
        score = record.report?.score,
        checksJson = json.encodeToString(
            serializer,
            record.report?.results.orEmpty().map { CheckJson(it.check.name, it.verdict.name, it.reason?.name, it.metrics, it.thresholds) },
        ),
        overrideReason = record.overrideReason,
        filePath = record.filePath,
        width = record.image?.width,
        height = record.image?.height,
        fileBytes = record.image?.fileBytes,
        orientation = record.image?.orientation,
        latitude = record.fix?.latitude,
        longitude = record.fix?.longitude,
        altitude = record.fix?.altitudeMeters,
        accuracyMeters = record.fix?.accuracyMeters,
        fixTimeMillis = record.fix?.fixTimeEpochMillis,
        locationProvider = record.fix?.provider,
        isMock = record.fix?.isMock ?: false,
        capturedAtMillis = record.capturedAtEpochMillis,
        analysisMillis = record.report?.analysisMillis,
        shutterMillis = record.shutterMillis,
        deviceModel = record.device.model,
        cameraId = record.device.cameraId,
        osVersion = record.device.osVersion,
        profileSchemaVersion = record.profileSchemaVersion,
    )

    fun toDomain(entity: CaptureEntity): CaptureRecord {
        val results = json.decodeFromString(serializer, entity.checksJson).mapNotNull { it.toDomain() }
        val report = entity.verdict?.let { verdict ->
            QualityReport(Stage.POST_CAPTURE, results, Verdict.valueOf(verdict), entity.score ?: 0, entity.analysisMillis ?: 0)
        }
        val image = if (entity.width != null && entity.height != null) {
            ImageInfo(entity.width, entity.height, entity.fileBytes ?: 0, entity.orientation ?: 0, null)
        } else {
            null
        }
        val fix = if (entity.latitude != null && entity.longitude != null) {
            LocationFix(
                entity.latitude, entity.longitude, entity.altitude, entity.accuracyMeters ?: 0f,
                entity.fixTimeMillis ?: 0, entity.locationProvider.orEmpty(), entity.isMock,
            )
        } else {
            null
        }
        return CaptureRecord(
            captureId = entity.id,
            requestId = entity.requestId,
            profileId = entity.profileId,
            status = CaptureStatus.valueOf(entity.status),
            report = report,
            overrideReason = entity.overrideReason,
            filePath = entity.filePath,
            image = image,
            fix = fix,
            capturedAtEpochMillis = entity.capturedAtMillis,
            shutterMillis = entity.shutterMillis,
            device = DeviceInfo(entity.deviceModel, entity.cameraId, entity.osVersion),
            profileSchemaVersion = entity.profileSchemaVersion,
        )
    }

    /** Unknown names from a newer SDK version are skipped rather than crashing an older reader. */
    private fun CheckJson.toDomain(): CheckResult? {
        val checkValue = QualityCheck.entries.firstOrNull { it.name == check } ?: return null
        val verdictValue = Verdict.entries.firstOrNull { it.name == verdict } ?: return null
        return CheckResult(checkValue, verdictValue, ReasonCode.entries.firstOrNull { it.name == reason }, metrics, thresholds)
    }
}
