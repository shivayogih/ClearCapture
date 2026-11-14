package com.clearcapture.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * Feature modules whose `src/main` Kotlin must take colours, sizes and components only from
 * `:designsystem` (DESIGN_SYSTEM.md section 9).
 */
internal val designTokenModules: Set<String> = setOf(":capture:ui", ":capture:api", ":capture:compose", ":core:camera")

/** One forbidden pattern and how to fix it. */
internal class RawValueRule(val name: String, val pattern: Regex, val fix: String)

/** The patterns the check refuses. Comments are blanked out before matching, so KDoc may mention them. */
internal val rawValueRules: List<RawValueRule> = listOf(
    RawValueRule(
        name = "raw colour",
        pattern = Regex("""\bColor\s*\(\s*(?:0x|0X|\d|red\s*=|green\s*=|blue\s*=|alpha\s*=)"""),
        fix = "use a CcTheme.colors token",
    ),
    RawValueRule(
        name = "named colour",
        pattern = Regex(
            """\bColor\s*\.\s*(?:Red|Green|Blue|Black|White|Gray|LightGray|DarkGray|Cyan|Magenta|Yellow)\b""",
        ),
        fix = "use a CcTheme.colors token",
    ),
    RawValueRule(
        name = "platform colour",
        pattern = Regex("""\bColor\s*\.\s*(?:parseColor|rgb|argb|valueOf)\s*\("""),
        fix = "use a CcTheme.colors token",
    ),
    RawValueRule(
        name = "raw dp size",
        pattern = Regex(
            """\.(?:size|height|width|requiredSize|requiredHeight|requiredWidth|defaultMinSize|sizeIn|heightIn|""" +
                """widthIn|requiredSizeIn|requiredHeightIn|requiredWidthIn)\s*\([^()]*?\b\d+(?:\.\d+)?f?\s*\.dp\b""",
        ),
        fix = "use a CcTheme.sizes token",
    ),
    RawValueRule(
        name = "Material import",
        pattern = Regex("""\bandroidx\.compose\.material3\b|\bandroidx\.compose\.material\."""),
        fix = "use a :designsystem component",
    ),
)

/** Finds forbidden patterns in Kotlin [source]; returns `line: rule (text)` entries. */
internal fun findRawValues(source: String): List<String> {
    val code = blankComments(source)
    val lineStarts = buildList {
        add(0)
        code.forEachIndexed { index, char -> if (char == '\n') add(index + 1) }
    }
    return rawValueRules.flatMap { rule ->
        rule.pattern.findAll(code).map { match ->
            val line = lineStarts.indexOfLast { it <= match.range.first } + 1
            "line $line: ${rule.name} '${match.value.replace(Regex("\\s+"), " ")}', ${rule.fix}"
        }
    }.sortedBy { it.substringAfter("line ").substringBefore(':').toInt() }
}

/** Replaces `//` and `/* */` comments with spaces, keeping line breaks and string literals. */
internal fun blankComments(source: String): String {
    val out = StringBuilder(source.length)
    var i = 0
    var inString = false
    while (i < source.length) {
        val c = source[i]
        val next = source.getOrNull(i + 1)
        when {
            inString -> {
                out.append(c)
                if (c == '\\' && next != null) {
                    out.append(next)
                    i++
                } else if (c == '"') {
                    inString = false
                }
            }
            c == '"' -> {
                inString = true
                out.append(c)
            }
            c == '/' && next == '/' -> {
                while (i < source.length && source[i] != '\n') {
                    out.append(' ')
                    i++
                }
                continue
            }
            c == '/' && next == '*' -> {
                val end = source.indexOf("*/", i + 2).let { if (it < 0) source.length else it + 2 }
                for (k in i until end) out.append(if (source[k] == '\n') '\n' else ' ')
                i = end
                continue
            }
            else -> out.append(c)
        }
        i++
    }
    return out.toString()
}

/**
 * Fails when a feature module's `src/main` Kotlin uses a raw colour, a raw dp literal as a size
 * or touch target, or a Material component directly, instead of `:designsystem` tokens and
 * components (DESIGN_SYSTEM.md section 9). Plain source scanning; inputs are a file collection,
 * so it is cacheable and configuration-cache compatible.
 */
@CacheableTask
abstract class CheckRawDesignValuesTask : DefaultTask() {

    /** Kotlin sources to scan. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    /** Directory that paths in the report are relative to. */
    @get:Internal
    abstract val baseDirectory: DirectoryProperty

    /** Report of the last run; also makes the task up to date when nothing changed. */
    @get:OutputFile
    abstract val report: RegularFileProperty

    /** Scans the sources and fails on the first module-level set of findings. */
    @TaskAction
    fun check() {
        val base = baseDirectory.get().asFile
        val findings = sources.files.sortedBy { it.path }.flatMap { file ->
            findRawValues(file.readText()).map { "${file.relativeTo(base).invariantSeparatorsPath}, $it" }
        }
        val reportFile = report.get().asFile
        reportFile.parentFile.mkdirs()
        reportFile.writeText(if (findings.isEmpty()) "No raw design values.\n" else findings.joinToString("\n", postfix = "\n"))
        if (findings.isNotEmpty()) {
            throw GradleException(
                "Raw design values in a feature module (docs/DESIGN_SYSTEM.md section 9):\n" +
                    findings.joinToString("\n") { " - $it" },
            )
        }
    }
}

/** Registers `checkRawDesignValues` in the feature modules and makes `check` depend on it. */
internal fun Project.registerRawDesignValuesCheck() {
    if (path !in designTokenModules) return
    val task = tasks.register("checkRawDesignValues", CheckRawDesignValuesTask::class.java) {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Fails when src/main uses raw colours, raw dp sizes or Material components."
        sources.from(fileTree("src/main") { include("**/*.kt") })
        baseDirectory.set(layout.projectDirectory)
        report.set(layout.buildDirectory.file("reports/rawDesignValues/report.txt"))
    }
    tasks.matching { it.name == LifecycleBasePlugin.CHECK_TASK_NAME }.configureEach { dependsOn(task) }
}
