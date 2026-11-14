package com.clearcapture.clearcapture.domain

import com.clearcapture.clearcapture.domain.model.CheckResult
import com.clearcapture.clearcapture.domain.model.QualityCheck
import com.clearcapture.clearcapture.domain.model.QualityReport
import com.clearcapture.clearcapture.domain.model.ReasonCode
import com.clearcapture.clearcapture.domain.model.Stage
import com.clearcapture.clearcapture.domain.model.Verdict

internal fun result(check: QualityCheck, verdict: Verdict, reason: ReasonCode? = null) =
    CheckResult(check, verdict, reason, emptyMap(), emptyMap())

internal fun liveReport(vararg results: CheckResult) =
    QualityReport(Stage.LIVE, results.toList(), results.map { it.verdict }.maxByOrNull { it.ordinal } ?: Verdict.PASS, 100, 5)
