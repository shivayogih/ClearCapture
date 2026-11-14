package com.clearcapture.clearcapture.domain.usecase

import app.cash.turbine.test
import com.clearcapture.clearcapture.domain.fakes.FakeClock
import com.clearcapture.clearcapture.domain.fakes.FakeLocationSource
import com.clearcapture.clearcapture.domain.fakes.FakeQualityAnalyzer
import com.clearcapture.clearcapture.domain.hint.GuideStatus
import com.clearcapture.clearcapture.domain.hint.HintKind
import com.clearcapture.clearcapture.domain.liveReport
import com.clearcapture.clearcapture.domain.model.LocationFix
import com.clearcapture.clearcapture.domain.model.LocationStatus
import com.clearcapture.clearcapture.domain.model.QualityCheck
import com.clearcapture.clearcapture.domain.model.QualityReport
import com.clearcapture.clearcapture.domain.model.ReasonCode
import com.clearcapture.clearcapture.domain.model.TestProfiles
import com.clearcapture.clearcapture.domain.model.Verdict
import com.clearcapture.clearcapture.domain.result
import com.clearcapture.clearcapture.domain.session.CaptureSession
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LiveGuidanceTest {

    private val fix = LocationFix(1.0, 2.0, null, 5f, 0, "gps", false)

    private fun TestScope.setup(): Triple<ObserveLiveQualityUseCase, MutableStateFlow<QualityReport?>, FakeLocationSource> {
        val clock = object : com.clearcapture.clearcapture.common.Clock {
            override fun nowMillis() = testScheduler.currentTime
            override fun elapsedNanos() = testScheduler.currentTime * 1_000_000
        }
        val reports = MutableStateFlow<QualityReport?>(null)
        val location = FakeLocationSource(LocationStatus.Ready(fix.copy(fixTimeEpochMillis = 0)))
        return Triple(ObserveLiveQualityUseCase(reports, MutableStateFlow(true), location, clock), reports, location)
    }

    @Test
    fun `ready appears after the hold and enables capture`() = runTest {
        val (useCase, reports, _) = setup()
        reports.value = liveReport(result(QualityCheck.BLUR, Verdict.PASS))
        useCase(TestProfiles.container()).test {
            assertThat(awaitItem().hint.kind).isEqualTo(HintKind.NONE)
            val ready = awaitItem()
            assertThat(ready.hint.kind).isEqualTo(HintKind.READY)
            assertThat(ready.guideStatus).isEqualTo(GuideStatus.Ready)
            assertThat(ready.captureEnabled).isTrue()
            assertThat(testScheduler.currentTime).isAtLeast(400)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `losing location disables capture at once, before the hint hold`() = runTest {
        val (useCase, reports, location) = setup()
        reports.value = liveReport(result(QualityCheck.BLUR, Verdict.PASS))
        useCase(TestProfiles.container()).test {
            skipItems(1)
            assertThat(awaitItem().captureEnabled).isTrue()
            location.state.value = LocationStatus.ProviderDisabled
            assertThat(awaitItem().captureEnabled).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `blocking blur disables capture with a problem guide`() = runTest {
        val (useCase, reports, _) = setup()
        reports.value = liveReport(result(QualityCheck.BLUR, Verdict.FAIL, ReasonCode.BLUR_MOTION))
        useCase(TestProfiles.container()).test {
            skipItems(1)
            val blur = awaitItem()
            assertThat(blur.hint.kind).isEqualTo(HintKind.BLUR)
            assertThat(blur.guideStatus).isEqualTo(GuideStatus.Problem)
            assertThat(blur.captureEnabled).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `session starts location and warms up the analyzer once`() = runTest {
        val location = FakeLocationSource()
        val analyzer = FakeQualityAnalyzer()
        val session = CaptureSession(location, analyzer, this)
        session.start(TestProfiles.defaultLocation)
        session.start(TestProfiles.defaultLocation)
        advanceUntilIdle()
        assertThat(location.startedWith).isEqualTo(TestProfiles.defaultLocation)
        assertThat(session.analyzerAvailable.value).isTrue()
        session.close()
        assertThat(location.stopped).isTrue()
    }
}
