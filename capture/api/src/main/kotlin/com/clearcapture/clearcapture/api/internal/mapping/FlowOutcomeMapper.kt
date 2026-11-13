package com.clearcapture.clearcapture.api.internal.mapping

import android.net.Uri
import com.clearcapture.clearcapture.api.CaptureResult
import com.clearcapture.clearcapture.api.CheckSummary
import com.clearcapture.clearcapture.api.GeoStamp
import com.clearcapture.clearcapture.api.QualitySummary
import com.clearcapture.clearcapture.domain.model.AcceptedCapture
import com.clearcapture.clearcapture.domain.model.FlowOutcome
import com.clearcapture.clearcapture.domain.model.LocationFix
import com.clearcapture.clearcapture.domain.model.QualityReport

/** Maps the domain [FlowOutcome] to the public [CaptureResult]. The only place the two meet. */
internal object FlowOutcomeMapper {

    fun map(outcome: FlowOutcome, requestId: String): CaptureResult = when (outcome) {
        is FlowOutcome.Accepted -> accepted(outcome.capture, requestId)
        FlowOutcome.Cancelled -> CaptureResult.Cancelled
        is FlowOutcome.Failed -> CaptureResult.Failed(outcome.code.name, outcome.detail)
    }

    private fun accepted(capture: AcceptedCapture, requestId: String) = CaptureResult.Accepted(
        captureId = capture.captureId,
        imageUri = Uri.fromFile(capture.file),
        profileId = capture.profileId,
        quality = summary(capture.report, capture.overrideReason),
        location = capture.fix?.let(::geoStamp),
        capturedAtEpochMillis = capture.capturedAtEpochMillis,
        requestId = requestId,
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
