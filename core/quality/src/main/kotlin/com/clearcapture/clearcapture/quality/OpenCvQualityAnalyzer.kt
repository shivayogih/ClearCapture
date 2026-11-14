package com.clearcapture.clearcapture.quality

import com.clearcapture.clearcapture.common.Clock
import com.clearcapture.clearcapture.common.Logger
import com.clearcapture.clearcapture.common.Outcome
import com.clearcapture.clearcapture.domain.model.CaptureError
import com.clearcapture.clearcapture.domain.model.CaptureProfile
import com.clearcapture.clearcapture.domain.model.CheckResult
import com.clearcapture.clearcapture.domain.model.FrameData
import com.clearcapture.clearcapture.domain.model.QualityCheck
import com.clearcapture.clearcapture.domain.model.QualityReport
import com.clearcapture.clearcapture.domain.model.ReasonCode
import com.clearcapture.clearcapture.domain.model.Stage
import com.clearcapture.clearcapture.domain.model.Verdict
import com.clearcapture.clearcapture.domain.port.QualityAnalyzer
import com.clearcapture.clearcapture.opencv.DecodedImage
import com.clearcapture.clearcapture.opencv.FrameMats
import com.clearcapture.clearcapture.opencv.JpegDecoder
import com.clearcapture.clearcapture.opencv.JpegHeader
import com.clearcapture.clearcapture.opencv.JpegHeaderReader
import com.clearcapture.clearcapture.opencv.MatPool
import com.clearcapture.clearcapture.opencv.OpenCvInitializer
import com.clearcapture.clearcapture.opencv.OpenCvStatus
import com.clearcapture.clearcapture.quality.FrameMatsAdapter.toNormalized
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.opencv.core.CvException
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The domain [QualityAnalyzer] on OpenCV (LLD 5). Validators come from [ValidatorRegistry] and are filtered by
 * stage, by the profile's `requiredChecks` and by whether they apply to the profile. `LOCATION` is not
 * measured here; the capture use case adds it.
 *
 * Live: synchronous on the caller's thread; checks with [QualityValidator.liveEveryNthFrame] > 1 (framing,
 * obstruction) run on every third frame and repeat their last result in between.
 * Post-capture: the file is decoded once (LLD 5.0) and the checks run concurrently on [dispatcher].
 * Without OpenCV (D15) the resolution check still runs and every OpenCV-based required check is
 * FAIL / ANALYSIS_UNAVAILABLE post-capture, WARNING / ANALYSIS_UNAVAILABLE live.
 */
internal class OpenCvQualityAnalyzer(
    private val initializer: OpenCvInitializer,
    private val validators: List<QualityValidator>,
    private val pool: MatPool,
    private val decoder: JpegDecoder,
    private val dispatcher: CoroutineDispatcher,
    private val clock: Clock = Clock.System,
    private val logger: Logger = Logger.None,
) : QualityAnalyzer {

    private val liveLock = Any()
    private var liveProfile: CaptureProfile? = null
    private var liveFrameIndex = 0L
    private val liveCache = HashMap<QualityCheck, CheckResult>()

    override suspend fun warmUp(): Outcome<Unit, CaptureError> = when (val status = initializer.initialize()) {
        OpenCvStatus.Loaded -> Outcome.Success(Unit)
        is OpenCvStatus.Failed -> {
            logger.warn(TAG, "OpenCV could not be loaded: ${status.cause}")
            Outcome.Failure(CaptureError.AnalyzerUnavailable)
        }
        OpenCvStatus.NotLoaded -> Outcome.Failure(CaptureError.AnalyzerUnavailable)
    }

    override fun analyzeLive(frame: FrameData, profile: CaptureProfile): QualityReport {
        val start = clock.elapsedNanos()
        val selected = select(Stage.LIVE, profile)
        val results = if (initializer.isLoaded) {
            runLive(frame, profile, selected)
        } else {
            selected.map { analysisUnavailable(it.check, Stage.LIVE) }
        }
        return QualityScoreCalculator.report(Stage.LIVE, results, profile, elapsedMillis(start))
    }

    override suspend fun analyzeFile(
        file: File,
        profile: CaptureProfile,
        expectedCaptureLongSide: Int?,
    ): Outcome<QualityReport, CaptureError> = withContext(dispatcher) {
        val start = clock.elapsedNanos()
        val header = JpegHeaderReader.read(file)
        if (header == null) {
            logger.warn(TAG, "Saved photo is unreadable or not a JPEG (${file.length()} bytes)")
            return@withContext Outcome.Failure(CaptureError.FileUnreadable(file.path))
        }
        val selected = select(Stage.POST_CAPTURE, profile)
        val results = if (initializer.initialize() == OpenCvStatus.Loaded) {
            analyzeDecoded(file, header, profile, selected, expectedCaptureLongSide)
        } else {
            val input = ValidationInput(Stage.POST_CAPTURE, null, SavedImageFacts.of(header, null, expectedCaptureLongSide))
            selected.map { validator ->
                if (validator.needsOpenCv) analysisUnavailable(validator.check, Stage.POST_CAPTURE) else validator.validate(input, profile)
            }
        }
        Outcome.Success(QualityScoreCalculator.report(Stage.POST_CAPTURE, results, profile, elapsedMillis(start)))
    }

    private fun select(stage: Stage, profile: CaptureProfile): List<QualityValidator> = validators.filter {
        stage in it.stages && it.check in profile.requiredChecks && it.check != QualityCheck.LOCATION && it.appliesTo(profile)
    }

    private fun runLive(frame: FrameData, profile: CaptureProfile, selected: List<QualityValidator>): List<CheckResult> =
        synchronized(liveLock) {
            if (profile != liveProfile) {
                liveCache.clear()
                liveProfile = profile
                liveFrameIndex = 0
            }
            val index = liveFrameIndex++
            FrameMatsAdapter.live(frame, profile.guide, pool).use { mats ->
                val input = ValidationInput(Stage.LIVE, mats, null)
                selected.map { validator ->
                    val cached = liveCache[validator.check]
                    if (cached != null && index % validator.liveEveryNthFrame != 0L) {
                        cached
                    } else {
                        validator.validate(input, profile).also { liveCache[validator.check] = it }
                    }
                }
            }
        }

    private suspend fun analyzeDecoded(
        file: File,
        header: JpegHeader,
        profile: CaptureProfile,
        selected: List<QualityValidator>,
        expectedLongSide: Int?,
    ): List<CheckResult> {
        val guide = profile.guide?.toNormalized()
        val decoded: DecodedImage? = try {
            decoder.decode(file, header, guide)
        } catch (_: CvException) {
            null
        }
        val facts = SavedImageFacts.of(header, decoded != null, expectedLongSide)
        if (decoded == null) {
            logger.warn(TAG, "Saved photo could not be decoded (${header.fileBytes} bytes)")
            val input = ValidationInput(Stage.POST_CAPTURE, null, facts)
            return selected.map { validator ->
                if (validator.needsOpenCv) corrupt(validator.check) else validator.validate(input, profile)
            }
        }
        return FrameMats.fromDecoded(decoded, guide, pool).use { mats ->
            val input = ValidationInput(Stage.POST_CAPTURE, mats, facts)
            coroutineScope {
                selected.map { validator -> async { validator.validate(input, profile) } }.awaitAll()
            }
        }
    }

    private fun corrupt(check: QualityCheck): CheckResult =
        CheckResult(check, Verdict.FAIL, ReasonCode.FILE_CORRUPT, emptyMap(), emptyMap())

    private fun elapsedMillis(startNanos: Long): Long = TimeUnit.NANOSECONDS.toMillis(clock.elapsedNanos() - startNanos)

    private companion object {
        const val TAG = "ClearCapture.Quality"
    }
}
