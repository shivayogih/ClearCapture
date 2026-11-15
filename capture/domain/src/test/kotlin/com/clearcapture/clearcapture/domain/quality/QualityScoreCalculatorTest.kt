package com.clearcapture.clearcapture.domain.quality

import com.clearcapture.clearcapture.domain.model.QualityCheck.BLUR
import com.clearcapture.clearcapture.domain.model.QualityCheck.EXPOSURE
import com.clearcapture.clearcapture.domain.model.QualityCheck.FRAMING
import com.clearcapture.clearcapture.domain.model.QualityCheck.GLARE
import com.clearcapture.clearcapture.domain.model.ReasonCode
import com.clearcapture.clearcapture.domain.model.Stage
import com.clearcapture.clearcapture.domain.model.TestProfiles
import com.clearcapture.clearcapture.domain.model.Verdict
import com.clearcapture.clearcapture.domain.result
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QualityScoreCalculatorTest {

    private val profile = TestProfiles.container() // FRAMING and OBSTRUCTION are warning-only

    private fun combine(vararg results: com.clearcapture.clearcapture.domain.model.CheckResult) =
        QualityScoreCalculator.combine(Stage.POST_CAPTURE, results.toList(), profile, 12)

    @Test
    fun `all pass scores 100`() {
        val report = combine(result(BLUR, Verdict.PASS), result(EXPOSURE, Verdict.PASS))
        assertThat(report.verdict).isEqualTo(Verdict.PASS)
        assertThat(report.score).isEqualTo(100)
    }

    @Test
    fun `worst result wins`() {
        assertThat(combine(result(BLUR, Verdict.PASS), result(EXPOSURE, Verdict.WARNING, ReasonCode.TOO_DARK)).verdict)
            .isEqualTo(Verdict.WARNING)
        assertThat(combine(result(GLARE, Verdict.WARNING, ReasonCode.GLARE_ON_SUBJECT), result(BLUR, Verdict.FAIL, ReasonCode.BLUR_FOCUS)).verdict)
            .isEqualTo(Verdict.FAIL)
    }

    @Test
    fun `fail of a warning-only check is downgraded`() {
        val report = combine(result(FRAMING, Verdict.FAIL, ReasonCode.SUBJECT_CUT_OFF))
        assertThat(report.verdict).isEqualTo(Verdict.WARNING)
        assertThat(report.results.single().verdict).isEqualTo(Verdict.WARNING)
        assertThat(report.score).isEqualTo(85)
    }

    @Test
    fun `analysis unavailable is never downgraded`() {
        val report = combine(result(FRAMING, Verdict.FAIL, ReasonCode.ANALYSIS_UNAVAILABLE))
        assertThat(report.verdict).isEqualTo(Verdict.FAIL)
    }

    @Test
    fun `score arithmetic and floor`() {
        assertThat(combine(result(BLUR, Verdict.FAIL, ReasonCode.BLUR_FOCUS), result(GLARE, Verdict.WARNING, ReasonCode.GLARE_ON_SUBJECT)).score)
            .isEqualTo(45)
        val manyFails = (1..3).map { result(BLUR, Verdict.FAIL, ReasonCode.BLUR_FOCUS) }.toTypedArray()
        assertThat(combine(*manyFails).score).isEqualTo(0)
    }
}
