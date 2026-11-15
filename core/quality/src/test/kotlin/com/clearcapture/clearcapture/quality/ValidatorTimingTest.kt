package com.clearcapture.clearcapture.quality

import com.clearcapture.clearcapture.domain.model.QualityCheck
import com.clearcapture.clearcapture.domain.model.Stage
import com.clearcapture.clearcapture.domain.model.TestProfiles
import com.clearcapture.clearcapture.opencv.DecodedImage
import com.clearcapture.clearcapture.opencv.FrameMats
import com.clearcapture.clearcapture.opencv.JpegDecoder
import com.clearcapture.clearcapture.opencv.JpegHeaderReader
import com.clearcapture.clearcapture.opencv.MatPool
import com.clearcapture.clearcapture.opencv.OpenCvInitializer
import com.clearcapture.clearcapture.quality.FrameMatsAdapter.toNormalized
import com.clearcapture.clearcapture.quality.testing.DesktopOpenCv
import com.clearcapture.clearcapture.quality.testing.Fixtures
import com.clearcapture.clearcapture.quality.testing.ImgcodecsImageCodec
import com.clearcapture.clearcapture.quality.testing.LiveFrames
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.opencv.core.Core
import java.io.File
import kotlin.math.ceil

/**
 * Per-validator time on this desktop JVM, live (640×480) and post-capture (normalised 2000 px image), as median
 * and p95 over [RUNS] runs after [WARM_UP] warm-up runs. Written to `build/reports/validator-timings.md`.
 * Desktop numbers only: they show relative cost, not what a tablet will do. Nothing is asserted on time.
 */
class ValidatorTimingTest {
    private val profile = TestProfiles.container()
    private val pool = MatPool()
    private val validators = ValidatorRegistry.create(pool)

    init {
        DesktopOpenCv.load()
    }

    /** Accumulates only the time spent inside [time], so set-up and clean-up stay out of the numbers. */
    private class Stopwatch {
        var nanos = 0L

        fun <T> time(block: () -> T): T {
            val start = System.nanoTime()
            try {
                return block()
            } finally {
                nanos += System.nanoTime() - start
            }
        }
    }

    private fun measure(block: (Stopwatch) -> Unit): Pair<Double, Double> {
        repeat(WARM_UP) { block(Stopwatch()) }
        val samples = (1..RUNS).map { Stopwatch().also(block).nanos / NANOS_PER_MILLI }.sorted()
        val median = (samples[(RUNS - 1) / 2] + samples[RUNS / 2]) / 2
        val p95 = samples[ceil(P95 * RUNS).toInt() - 1]
        return median to p95
    }

    @Test
    fun `report validator timings`() {
        val rows = mutableListOf<String>()
        fun row(stage: String, name: String, timing: Pair<Double, Double>) {
            rows += "| $stage | $name | ${"%.2f".format(timing.first)} | ${"%.2f".format(timing.second)} |"
        }

        // Live: a fresh frame per run, as the camera delivers; the shared grey conversion is timed on its own.
        val bgr = Fixtures.bgr(Fixtures.SHARP)
        val frame = LiveFrames.fromBgr(bgr, interleaved = true)
        bgr.release()
        row("Live 640×480", "FrameMats.gray (Y plane, rotate/copy)", measure { sw ->
            FrameMatsAdapter.live(frame, profile.guide, pool).use { sw.time { it.gray } }
        })
        validators.filter { Stage.LIVE in it.stages }.forEach { validator ->
            row("Live 640×480", validator.javaClass.simpleName + glareNote(validator), measure { sw ->
                FrameMatsAdapter.live(frame, profile.guide, pool).use { mats ->
                    mats.gray
                    sw.time { validator.validate(ValidationInput(Stage.LIVE, mats, null), profile) }
                }
            })
        }
        val initializer = OpenCvInitializer(Dispatchers.Default, DesktopOpenCv.loader)
        val analyzer = OpenCvQualityAnalyzer(initializer, validators, pool, JpegDecoder(ImgcodecsImageCodec()), Dispatchers.Default)
        runBlocking { analyzer.warmUp() }
        row("Live 640×480", "analyzeLive, all checks (every frame)", measure { sw ->
            val fresh = OpenCvQualityAnalyzer(initializer, validators, pool, JpegDecoder(ImgcodecsImageCodec()), Dispatchers.Default)
            sw.time { fresh.analyzeLive(frame, profile) }
        })

        // Post-capture: decode once, then give each run its own copy of the decoded mats.
        val file = Fixtures.file(Fixtures.SHARP)
        val header = checkNotNull(JpegHeaderReader.read(file))
        val decoder = JpegDecoder(ImgcodecsImageCodec())
        val guide = profile.guide?.toNormalized()
        row("Post-capture", "JpegHeaderReader.read", measure { sw -> sw.time { JpegHeaderReader.read(file) } })
        row("Post-capture", "JpegDecoder.decode (Imgcodecs, JVM)", measure { sw ->
            sw.time { decoder.decode(file, header, guide) }?.let { FrameMats.fromDecoded(it, guide, pool).close() }
        })
        val decoded = checkNotNull(decoder.decode(file, header, guide))
        val facts = SavedImageFacts.of(header, true, null)
        validators.filter { Stage.POST_CAPTURE in it.stages }.forEach { validator ->
            row("Post-capture", validator.javaClass.simpleName + glareNote(validator), measure { sw ->
                val copy = DecodedImage(decoded.analysisBgr.clone(), decoded.sharpCrop?.clone())
                FrameMats.fromDecoded(copy, guide, pool).use { mats ->
                    mats.gray
                    sw.time { validator.validate(ValidationInput(Stage.POST_CAPTURE, mats, facts), profile) }
                }
            })
        }
        row("Post-capture", "analyzeFile end to end (decode + concurrent checks)", measure { sw ->
            sw.time { runBlocking { analyzer.analyzeFile(file, profile) } }
        })
        decoded.analysisBgr.release()
        decoded.sharpCrop?.release()
        assertThat(pool.outstanding).isEqualTo(0)

        val report = buildString {
            appendLine("# Validator timings (desktop JVM)")
            appendLine()
            appendLine("Desktop JVM numbers, not device numbers: ${System.getProperty("os.name")} ${System.getProperty("os.arch")}, ")
            appendLine("${Runtime.getRuntime().availableProcessors()} logical CPUs, Java ${System.getProperty("java.version")}, ")
            appendLine("OpenCV ${Core.VERSION} (org.openpnp desktop natives). $RUNS runs after $WARM_UP warm-up runs; times in ms.")
            appendLine("Other builds may run on the same machine, so p95 values are noisy.")
            appendLine("Live frames are 640×480 YUV_420_888 (interleaved chroma) built from `sharp.jpg`; post-capture uses")
            appendLine("`sharp.jpg` (2000×1500) and the container profile. Validator rows time `validate` only: building the")
            appendLine("frame's grey mat is a separate row (live) or part of decoding (post-capture).")
            appendLine()
            appendLine("| Stage | Step | Median | p95 |")
            appendLine("|---|---|---:|---:|")
            rows.forEach(::appendLine)
        }
        println(report)
        File("build/reports").mkdirs()
        File("build/reports/validator-timings.md").writeText(report)
    }

    private fun glareNote(validator: QualityValidator) =
        if (validator.check == QualityCheck.GLARE) " (incl. BGR/HSV build)" else ""

    private companion object {
        const val WARM_UP = 5
        const val RUNS = 30
        const val P95 = 0.95
        const val NANOS_PER_MILLI = 1_000_000.0
    }
}
