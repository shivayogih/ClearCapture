package com.clearcapture.clearcapture.quality

import com.clearcapture.clearcapture.common.Outcome
import com.clearcapture.clearcapture.common.getOrNull
import com.clearcapture.clearcapture.domain.model.CaptureError
import com.clearcapture.clearcapture.domain.model.CaptureProfile
import com.clearcapture.clearcapture.domain.model.CheckResult
import com.clearcapture.clearcapture.domain.model.QualityCheck
import com.clearcapture.clearcapture.domain.model.QualityReport
import com.clearcapture.clearcapture.domain.model.ReasonCode
import com.clearcapture.clearcapture.domain.model.Stage
import com.clearcapture.clearcapture.domain.model.TestProfiles
import com.clearcapture.clearcapture.domain.model.Verdict
import com.clearcapture.clearcapture.opencv.JpegDecoder
import com.clearcapture.clearcapture.opencv.MatPool
import com.clearcapture.clearcapture.opencv.OpenCvInitializer
import com.clearcapture.clearcapture.opencv.OpenCvLibraryLoader
import com.clearcapture.clearcapture.quality.testing.DesktopOpenCv
import com.clearcapture.clearcapture.quality.testing.Fixtures
import com.clearcapture.clearcapture.quality.testing.ImgcodecsImageCodec
import com.clearcapture.clearcapture.quality.testing.LiveFrames
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OpenCvQualityAnalyzerTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val container = TestProfiles.container()
    private val general = TestProfiles.general()
    private val pool = MatPool()

    private fun analyzer(
        dispatcher: CoroutineDispatcher,
        loader: OpenCvLibraryLoader = DesktopOpenCv.loader,
        validators: List<QualityValidator> = ValidatorRegistry.create(pool),
    ) = OpenCvQualityAnalyzer(
        initializer = OpenCvInitializer(dispatcher, loader),
        validators = validators,
        pool = pool,
        decoder = JpegDecoder(ImgcodecsImageCodec()),
        dispatcher = dispatcher,
    )

    private fun TestScope.testAnalyzer(
        loader: OpenCvLibraryLoader = DesktopOpenCv.loader,
        validators: List<QualityValidator> = ValidatorRegistry.create(pool),
    ) = analyzer(StandardTestDispatcher(testScheduler), loader, validators)

    private fun QualityReport.checks() = results.map { it.check }

    private val failingLoader = OpenCvLibraryLoader { false }

    @Test
    fun `warm up succeeds when the library loads`() = runTest {
        assertThat(testAnalyzer().warmUp()).isEqualTo(Outcome.Success(Unit))
    }

    @Test
    fun `warm up reports AnalyzerUnavailable instead of throwing`() = runTest {
        val throwing = OpenCvLibraryLoader { throw UnsatisfiedLinkError("no opencv_java4") }
        assertThat(testAnalyzer(failingLoader).warmUp()).isEqualTo(Outcome.Failure(CaptureError.AnalyzerUnavailable))
        assertThat(testAnalyzer(throwing).warmUp()).isEqualTo(Outcome.Failure(CaptureError.AnalyzerUnavailable))
    }

    @Test
    fun `sharp photo passes every required check except location, which is not measured here`() = runTest {
        val report = checkNotNull(testAnalyzer().analyzeFile(Fixtures.file(Fixtures.SHARP), container).getOrNull())
        assertThat(report.stage).isEqualTo(Stage.POST_CAPTURE)
        assertThat(report.checks()).containsExactly(
            QualityCheck.BLUR, QualityCheck.EXPOSURE, QualityCheck.GLARE,
            QualityCheck.FRAMING, QualityCheck.RESOLUTION, QualityCheck.OBSTRUCTION,
        ).inOrder()
        assertThat(report.verdict).isEqualTo(Verdict.PASS)
        assertThat(report.score).isEqualTo(100)
        assertThat(pool.outstanding).isEqualTo(0)
    }

    @Test
    fun `only the profile's required checks run`() = runTest {
        val report = checkNotNull(testAnalyzer().analyzeFile(Fixtures.file(Fixtures.SHARP), general).getOrNull())
        assertThat(report.checks()).containsExactly(
            QualityCheck.BLUR, QualityCheck.EXPOSURE, QualityCheck.RESOLUTION, QualityCheck.OBSTRUCTION,
        ).inOrder()
    }

    @Test
    fun `checks run concurrently on a real dispatcher and warning-only failures are downgraded`() = runBlocking {
        val report = checkNotNull(analyzer(Dispatchers.Default).analyzeFile(Fixtures.file(Fixtures.OBSTRUCTED), container).getOrNull())
        val obstruction = checkNotNull(report.resultOf(QualityCheck.OBSTRUCTION))
        assertThat(obstruction.verdict to obstruction.reason).isEqualTo(Verdict.WARNING to ReasonCode.OBSTRUCTED)
        assertThat(pool.outstanding).isEqualTo(0)
    }

    @Test
    fun `without OpenCV the resolution check runs and every other required check fails as unavailable (D15)`() = runTest {
        val report = checkNotNull(testAnalyzer(failingLoader).analyzeFile(Fixtures.file(Fixtures.SHARP), container).getOrNull())
        val resolution = checkNotNull(report.resultOf(QualityCheck.RESOLUTION))
        assertThat(resolution.verdict).isEqualTo(Verdict.PASS)
        assertThat(resolution.metrics["width"]).isEqualTo(2000.0)
        val others = report.results - resolution
        assertThat(others.map { it.check }).containsExactly(
            QualityCheck.BLUR, QualityCheck.EXPOSURE, QualityCheck.GLARE, QualityCheck.FRAMING, QualityCheck.OBSTRUCTION,
        )
        others.forEach {
            // FRAMING and OBSTRUCTION are warning-only but ANALYSIS_UNAVAILABLE is never downgraded (finding 33).
            assertThat(it.verdict).isEqualTo(Verdict.FAIL)
            assertThat(it.reason).isEqualTo(ReasonCode.ANALYSIS_UNAVAILABLE)
            assertThat(it.metrics).isEmpty()
        }
        assertThat(report.verdict).isEqualTo(Verdict.FAIL)
    }

    @Test
    fun `without OpenCV a low-resolution photo still fails on resolution`() = runTest {
        val report = checkNotNull(testAnalyzer(failingLoader).analyzeFile(Fixtures.file(Fixtures.LOW_RESOLUTION), general).getOrNull())
        assertThat(report.resultOf(QualityCheck.RESOLUTION)?.reason).isEqualTo(ReasonCode.LOW_RESOLUTION)
    }

    @Test
    fun `a truncated photo fails as corrupt`() = runTest {
        val report = checkNotNull(testAnalyzer().analyzeFile(Fixtures.file(Fixtures.TRUNCATED), container).getOrNull())
        assertThat(report.resultOf(QualityCheck.RESOLUTION)?.reason).isEqualTo(ReasonCode.FILE_CORRUPT)
        assertThat(report.verdict).isEqualTo(Verdict.FAIL)
        assertThat(pool.outstanding).isEqualTo(0)
    }

    @Test
    fun `an expected capture size larger than the file is reported as downscaling`() = runTest {
        val report = testAnalyzer().analyzeFile(Fixtures.file(Fixtures.SHARP), container, expectedCaptureLongSide = 4000).getOrNull()
        assertThat(report?.resultOf(QualityCheck.RESOLUTION)?.reason).isEqualTo(ReasonCode.LOW_RESOLUTION)
    }

    @Test
    fun `a missing or non-JPEG file is FileUnreadable`() = runTest {
        val missing = temp.root.resolve("missing.jpg")
        val text = temp.newFile("note.jpg").apply { writeText("not an image") }
        assertThat(testAnalyzer().analyzeFile(missing, container)).isEqualTo(Outcome.Failure(CaptureError.FileUnreadable(missing.path)))
        assertThat(testAnalyzer().analyzeFile(text, container)).isEqualTo(Outcome.Failure(CaptureError.FileUnreadable(text.path)))
    }

    @Test
    fun `live analysis of a sharp frame passes the live checks`() = runTest {
        val analyzer = testAnalyzer()
        analyzer.warmUp()
        val report = analyzer.analyzeLive(LiveFrames.fromBgr(Fixtures.bgr(Fixtures.SHARP), interleaved = true), container)
        assertThat(report.stage).isEqualTo(Stage.LIVE)
        assertThat(report.checks()).containsExactly(
            QualityCheck.BLUR, QualityCheck.EXPOSURE, QualityCheck.GLARE, QualityCheck.FRAMING, QualityCheck.OBSTRUCTION,
        ).inOrder()
        assertThat(report.verdict).isEqualTo(Verdict.PASS)
        assertThat(pool.outstanding).isEqualTo(0)
    }

    @Test
    fun `live analysis before OpenCV is loaded warns instead of failing`() = runTest {
        val report = testAnalyzer(failingLoader).analyzeLive(LiveFrames.fromBgr(Fixtures.bgr(Fixtures.SHARP)), container)
        report.results.forEach {
            assertThat(it.verdict to it.reason).isEqualTo(Verdict.WARNING to ReasonCode.ANALYSIS_UNAVAILABLE)
        }
        assertThat(report.verdict).isEqualTo(Verdict.WARNING)
    }

    @Test
    fun `framing and obstruction run on every third live frame and repeat their result in between`() = runTest {
        val everyFrame = CountingValidator(QualityCheck.BLUR, every = 1)
        val everyThird = CountingValidator(QualityCheck.FRAMING, every = 3)
        val analyzer = testAnalyzer(validators = listOf(everyFrame, everyThird))
        analyzer.warmUp()
        val frame = LiveFrames.fromBgr(Fixtures.bgr(Fixtures.SHARP))
        val reports = (1..7).map { analyzer.analyzeLive(frame, container) }
        assertThat(everyFrame.calls).isEqualTo(7)
        assertThat(everyThird.calls).isEqualTo(3)
        assertThat(reports.map { it.resultOf(QualityCheck.FRAMING)?.metrics?.get("call") })
            .containsExactly(1.0, 1.0, 1.0, 2.0, 2.0, 2.0, 3.0).inOrder()
        // A different profile starts afresh.
        analyzer.analyzeLive(frame, container.copy(id = "other"))
        assertThat(everyThird.calls).isEqualTo(4)
        assertThat(ValidatorRegistry.create(pool).filter { it.liveEveryNthFrame == 3 }.map { it.check })
            .containsExactly(QualityCheck.FRAMING, QualityCheck.OBSTRUCTION)
    }

    @Test
    fun `live analysis builds colour mats only when glare is required`() = runTest {
        val probe = ColourProbe()
        val analyzer = testAnalyzer(validators = ValidatorRegistry.create(pool) + probe)
        analyzer.warmUp()
        val frame = LiveFrames.fromBgr(Fixtures.bgr(Fixtures.SHARP))
        analyzer.analyzeLive(frame, general)
        assertThat(probe.sawColour).containsExactly(false)
        analyzer.analyzeLive(frame, container)
        assertThat(probe.sawColour).containsExactly(false, true).inOrder()
    }

    /** Counts its calls and reports the call number as a metric. */
    private class CountingValidator(override val check: QualityCheck, every: Int) : QualityValidator {
        var calls = 0
        override val stages: Set<Stage> = setOf(Stage.LIVE)
        override val liveEveryNthFrame: Int = every
        override fun validate(input: ValidationInput, profile: CaptureProfile): CheckResult {
            calls++
            return CheckResult(check, Verdict.PASS, null, mapOf("call" to calls.toDouble()), emptyMap())
        }
    }

    /** Runs last in live analysis (posing as RESOLUTION, which has no live stage) and records whether colour was built. */
    private class ColourProbe : QualityValidator {
        val sawColour = mutableListOf<Boolean>()
        override val check: QualityCheck = QualityCheck.RESOLUTION
        override val stages: Set<Stage> = setOf(Stage.LIVE)
        override fun validate(input: ValidationInput, profile: CaptureProfile): CheckResult {
            sawColour += input.mats.isBgrBuilt || input.mats.isHsvBuilt
            return CheckResult(check, Verdict.PASS, null, emptyMap(), emptyMap())
        }
    }
}
