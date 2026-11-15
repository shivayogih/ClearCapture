package com.clearcapture.clearcapture.api.internal.mapping

import android.net.Uri
import com.clearcapture.clearcapture.api.CaptureResult
import com.clearcapture.clearcapture.api.CapturedImage
import com.clearcapture.clearcapture.api.CheckSummary
import com.clearcapture.clearcapture.api.GeoStamp
import com.clearcapture.clearcapture.api.QualitySummary
import com.clearcapture.clearcapture.domain.model.AcceptedCapture
import com.clearcapture.clearcapture.domain.model.FlowOutcome
import com.clearcapture.clearcapture.domain.model.LocationFix
import com.clearcapture.clearcapture.domain.model.QualityReport

/** Maps the domain [FlowOutcome] to the public [CaptureResult]. The only place the two meet. */
internal object FlowOutcomeMapper {

    fun map(outcome: FlowOutcome, requestId: String, references: Map<String, String>): CaptureResult = when (outcome) {
        is FlowOutcome.Completed -> CaptureResult.Completed(requestId, outcome.captures.map { image(it, references) })
        is FlowOutcome.Cancelled -> CaptureResult.Cancelled(requestId, outcome.captures.map { image(it, references) })
        is FlowOutcome.Failed -> CaptureResult.Failed(outcome.code.name, outcome.detail)
    }

    private fun image(capture: AcceptedCapture, references: Map<String, String>) = CapturedImage(
        captureId = capture.captureId,
        uri = Uri.parse(capture.uri),
        profileId = capture.profileId,
        label = capture.label,
        quality = summary(capture.report, capture.overrideReason),
        location = capture.fix?.let(::geoStamp),
        locationNote = capture.locationNote.name,
        capturedAtEpochMillis = capture.capturedAtEpochMillis,
        gpsTime = capture.gpsTime,
        sha256 = capture.sha256,
        width = capture.width,
        height = capture.height,
        fileBytes = capture.fileBytes,
        references = references,
        deliveryNote = capture.deliveryNote,
    )

    fun summary(report: QualityReport, overrideReason: String?): QualitySummary = QualitySummary(
        verdict = report.verdict.name,
        score = report.score,
        checks = report.results.map { CheckSummary(it.check.name, it.verdict.name, it.reason?.name, it.metrics) },
        overriddenReason = overrideReason,
    )

    private fun geoStamp(fix: LocationFix) = GeoStamp(
        latitude = fix.latitude,
        longitude = fix.longitude,
        altitudeMeters = fix.altitudeMeters,
        accuracyMeters = fix.accuracyMeters,
        fixTimeEpochMillis = fix.fixTimeEpochMillis,
        provider = fix.provider,
        isMock = fix.isMock,
    )
}
