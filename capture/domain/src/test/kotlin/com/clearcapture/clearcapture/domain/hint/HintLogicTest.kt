package com.clearcapture.clearcapture.domain.hint

import com.clearcapture.clearcapture.domain.liveReport
import com.clearcapture.clearcapture.domain.model.LocationFix
import com.clearcapture.clearcapture.domain.model.LocationStatus
import com.clearcapture.clearcapture.domain.model.QualityCheck
import com.clearcapture.clearcapture.domain.model.ReasonCode
import com.clearcapture.clearcapture.domain.model.TestProfiles
import com.clearcapture.clearcapture.domain.model.Verdict
import com.clearcapture.clearcapture.domain.result
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HintLogicTest {

    private val profile = TestProfiles.container()
    private val ready = LocationStatus.Ready(LocationFix(1.0, 2.0, null, 5f, 0, "gps", false))

    @Test
    fun `location blocked outranks everything and blocks`() {
        val hint = HintPrioritizer.candidate(liveReport(result(QualityCheck.BLUR, Verdict.FAIL, ReasonCode.BLUR_MOTION)),
            LocationStatus.Acquiring, profile, analyzerAvailable = false)
        assertThat(hint).isEqualTo(Hint(HintKind.LOCATION_BLOCKED, blocking = true))
    }

    @Test
    fun `analyzer unavailable does not block`() {
        assertThat(HintPrioritizer.candidate(null, ready, profile, analyzerAvailable = false))
            .isEqualTo(Hint(HintKind.ANALYZER_UNAVAILABLE, blocking = false))
    }

    @Test
    fun `priority order dark before blur before glare before framing before obstruction`() {
        val report = liveReport(
            result(QualityCheck.OBSTRUCTION, Verdict.WARNING, ReasonCode.OBSTRUCTED),
            result(QualityCheck.FRAMING, Verdict.WARNING, ReasonCode.SUBJECT_CUT_OFF),
            result(QualityCheck.GLARE, Verdict.WARNING, ReasonCode.GLARE_ON_SUBJECT),
            result(QualityCheck.BLUR, Verdict.WARNING, ReasonCode.BLUR_FOCUS),
            result(QualityCheck.EXPOSURE, Verdict.WARNING, ReasonCode.TOO_DARK),
        )
        assertThat(HintPrioritizer.candidate(report, ready, profile, true).kind).isEqualTo(HintKind.TOO_DARK)
    }

    @Test
    fun `live fail blocks only for checks that are not warning-only`() {
        val blur = HintPrioritizer.candidate(liveReport(result(QualityCheck.BLUR, Verdict.FAIL, ReasonCode.BLUR_MOTION)), ready, profile, true)
        assertThat(blur).isEqualTo(Hint(HintKind.BLUR, blocking = true))
        val framing = HintPrioritizer.candidate(
            liveReport(result(QualityCheck.FRAMING, Verdict.FAIL, ReasonCode.SUBJECT_CUT_OFF)), ready, profile, true,
        )
        assertThat(framing).isEqualTo(Hint(HintKind.SUBJECT_CUT_OFF, blocking = false))
    }

    @Test
    fun `nothing wrong is ready`() {
        assertThat(HintPrioritizer.candidate(liveReport(result(QualityCheck.BLUR, Verdict.PASS)), ready, profile, true).kind)
            .isEqualTo(HintKind.READY)
    }

    @Test
    fun `guide status follows the hint and never relies on colour alone`() {
        assertThat(HintPrioritizer.guideStatus(Hint(HintKind.READY, false), ready)).isEqualTo(GuideStatus.Ready)
        assertThat(HintPrioritizer.guideStatus(Hint(HintKind.GLARE, false), ready)).isEqualTo(GuideStatus.Warning)
        assertThat(HintPrioritizer.guideStatus(Hint(HintKind.BLUR, true), ready)).isEqualTo(GuideStatus.Problem)
        assertThat(HintPrioritizer.guideStatus(Hint(HintKind.LOCATION_BLOCKED, true), LocationStatus.Acquiring)).isEqualTo(GuideStatus.Neutral)
        assertThat(HintPrioritizer.guideStatus(Hint(HintKind.NONE, false), ready)).isEqualTo(GuideStatus.Neutral)
    }

    @Test
    fun `stabilizer changes only after the hold time`() {
        val stabilizer = HintStabilizer(holdMillis = 400)
        val dark = Hint(HintKind.TOO_DARK, false)
        assertThat(stabilizer.offer(dark, 0).kind).isEqualTo(HintKind.NONE)
        assertThat(stabilizer.offer(dark, 399).kind).isEqualTo(HintKind.NONE)
        assertThat(stabilizer.offer(dark, 400).kind).isEqualTo(HintKind.TOO_DARK)
    }

    @Test
    fun `stabilizer does not flicker when a condition hovers around its threshold`() {
        val stabilizer = HintStabilizer(holdMillis = 400)
        val ready = Hint(HintKind.READY, false)
        val dark = Hint(HintKind.TOO_DARK, false)
        stabilizer.offer(ready, 0)
        stabilizer.offer(ready, 400)
        var changes = 0
        var shown = stabilizer.current
        for (t in 500L..3_000L step 100) {
            val next = stabilizer.offer(if ((t / 100) % 2 == 0L) dark else ready, t)
            if (next != shown) changes++
            shown = next
        }
        assertThat(changes).isEqualTo(0)
        assertThat(shown.kind).isEqualTo(HintKind.READY)
    }

    @Test
    fun `stabilizer restarts the hold when the candidate changes`() {
        val stabilizer = HintStabilizer(holdMillis = 400)
        stabilizer.offer(Hint(HintKind.TOO_DARK, false), 0)
        stabilizer.offer(Hint(HintKind.BLUR, true), 300)
        assertThat(stabilizer.offer(Hint(HintKind.BLUR, true), 600).kind).isEqualTo(HintKind.NONE)
        assertThat(stabilizer.offer(Hint(HintKind.BLUR, true), 700).kind).isEqualTo(HintKind.BLUR)
    }
}
