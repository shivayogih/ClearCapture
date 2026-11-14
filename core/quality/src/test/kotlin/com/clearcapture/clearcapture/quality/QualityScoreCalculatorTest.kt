package com.clearcapture.clearcapture.quality

import com.clearcapture.clearcapture.domain.model.CheckResult
import com.clearcapture.clearcapture.domain.model.QualityCheck
import com.clearcapture.clearcapture.domain.model.ReasonCode
import com.clearcapture.clearcapture.domain.model.Stage
import com.clearcapture.clearcapture.domain.model.TestProfiles
import com.clearcapture.clearcapture.domain.model.Verdict
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QualityScoreCalculatorTest {
    // Container profile: FRAMING and OBSTRUCTION are warning-only.
    private val profile = TestProfiles.container()

    private fun result(check: QualityCheck, verdict: Verdict, reason: ReasonCode? = null) =
        CheckResult(check, verdict, if (verdict == Verdict.PASS) null else reason ?: ReasonCode.BLUR_FOCUS, emptyMap(), emptyMap())

    private fun report(vararg results: CheckResult) =
        QualityScoreCalculator.report(Stage.POST_CAPTURE, results.toList(), profile, analysisMillis = 12)

    @Test
    fun `all passing gives PASS and 100`() {
        val report = report(result(QualityCheck.BLUR, Verdict.PASS), result(QualityCheck.EXPOSURE, Verdict.PASS))
        assertThat(report.verdict).isEqualTo(Verdict.PASS)
        assertThat(report.score).isEqualTo(100)
        assertThat(report.analysisMillis).isEqualTo(12)
    }

    @Test
    fun `no results gives PASS and 100`() {
        assertThat(report().verdict).isEqualTo(Verdict.PASS)
        assertThat(report().score).isEqualTo(100)
    }

    @Test
    fun `worst result wins`() {
        val warning = report(result(QualityCheck.BLUR, Verdict.PASS), result(QualityCheck.GLARE, Verdict.WARNING))
        val fail = report(result(QualityCheck.GLARE, Verdict.WARNING), result(QualityCheck.BLUR, Verdict.FAIL))
        assertThat(warning.verdict).isEqualTo(Verdict.WARNING)
        assertThat(fail.verdict).isEqualTo(Verdict.FAIL)
    }

    @Test
    fun `a FAIL of a warning-only check is downgraded to WARNING with its reason kept`() {
        val report = report(result(QualityCheck.FRAMING, Verdict.FAIL, ReasonCode.SUBJECT_CUT_OFF), result(QualityCheck.BLUR, Verdict.PASS))
        assertThat(report.verdict).isEqualTo(Verdict.WARNING)
        assertThat(report.resultOf(QualityCheck.FRAMING)?.verdict).isEqualTo(Verdict.WARNING)
        assertThat(report.resultOf(QualityCheck.FRAMING)?.reason).isEqualTo(ReasonCode.SUBJECT_CUT_OFF)
        assertThat(report.score).isEqualTo(85)
    }

    @Test
    fun `a FAIL of a check that is not warning-only stays FAIL`() {
        val report = report(result(QualityCheck.GLARE, Verdict.FAIL, ReasonCode.GLARE_ON_SUBJECT))
        assertThat(report.verdict).isEqualTo(Verdict.FAIL)
    }

    @Test
    fun `ANALYSIS_UNAVAILABLE is never downgraded`() {
        val report = report(
            result(QualityCheck.FRAMING, Verdict.FAIL, ReasonCode.ANALYSIS_UNAVAILABLE),
            result(QualityCheck.OBSTRUCTION, Verdict.FAIL, ReasonCode.ANALYSIS_UNAVAILABLE),
        )
        assertThat(report.verdict).isEqualTo(Verdict.FAIL)
        assertThat(report.results.map { it.verdict }).containsExactly(Verdict.FAIL, Verdict.FAIL)
        assertThat(report.score).isEqualTo(20)
    }

    @Test
    fun `score is 100 minus 40 per FAIL and 15 per WARNING`() {
        val report = report(
            result(QualityCheck.BLUR, Verdict.FAIL),
            result(QualityCheck.EXPOSURE, Verdict.WARNING, ReasonCode.TOO_DARK),
            result(QualityCheck.GLARE, Verdict.WARNING, ReasonCode.GLARE_ON_SUBJECT),
            result(QualityCheck.RESOLUTION, Verdict.PASS),
        )
        assertThat(report.score).isEqualTo(100 - 40 - 15 - 15)
    }

    @Test
    fun `score is floored at zero`() {
        val report = report(
            result(QualityCheck.BLUR, Verdict.FAIL),
            result(QualityCheck.EXPOSURE, Verdict.FAIL, ReasonCode.TOO_DARK),
            result(QualityCheck.GLARE, Verdict.FAIL, ReasonCode.GLARE_ON_SUBJECT),
            result(QualityCheck.RESOLUTION, Verdict.WARNING, ReasonCode.LOW_RESOLUTION),
        )
        assertThat(report.score).isEqualTo(0)
    }

    @Test
    fun `downgraded results count as warnings in the score`() {
        val report = report(
            result(QualityCheck.FRAMING, Verdict.FAIL, ReasonCode.SUBJECT_TOO_SMALL),
            result(QualityCheck.OBSTRUCTION, Verdict.FAIL, ReasonCode.OBSTRUCTED),
        )
        assertThat(report.score).isEqualTo(70)
        assertThat(report.verdict).isEqualTo(Verdict.WARNING)
    }
}
