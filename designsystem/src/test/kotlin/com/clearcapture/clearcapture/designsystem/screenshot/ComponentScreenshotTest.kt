package com.clearcapture.clearcapture.designsystem.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.clearcapture.clearcapture.designsystem.preview.CcSample
import com.clearcapture.clearcapture.designsystem.preview.CcSampleFrame
import com.clearcapture.clearcapture.designsystem.preview.CcSamples
import com.clearcapture.clearcapture.designsystem.theme.CcAppearance
import com.clearcapture.clearcapture.designsystem.theme.CcTheme
import com.clearcapture.clearcapture.designsystem.theme.ClearCaptureThemeContent
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.ThresholdValidator
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every component in every state, light and dark, at 100 % and 200 % font scale (LLD section 13).
 * Golden images are in `src/test/screenshots`; `check` verifies them and
 * `./gradlew :designsystem:recordRoborazziDebug` records them again.
 *
 * At 200 % each case also asserts that no text is cut off or pushed outside the screen.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// SDK 34: Robolectric needs Java 21 for SDK 35 and later, and the project builds on JDK 17.
@Config(sdk = [34], qualifiers = "w400dp-h900dp-hdpi")
class ComponentScreenshotTest(
    private val component: String,
    private val state: String,
    private val theme: String,
    private val percent: Int,
) {
    // Parameters are plain values: they are created outside the Robolectric sandbox.
    private val sample: CcSample = CcSamples.single { it.component == component && it.state == state }
    private val dark = theme == DARK
    private val fontScale = percent / 100f
    private val name = "${component}_${state}_${theme}_$percent"
    private val path = "src/test/screenshots/$component/${state}_${theme}_$percent.png"

    /** Tolerates anti-aliasing differences between machines; a real change moves far more pixels. */
    private val options = RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(resultValidator = ThresholdValidator(0.005f)),
    )

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun screenshot() {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                ClearCaptureThemeContent(
                    appearance = CcAppearance(),
                    dark = dark,
                    reduceMotion = true,
                    isTablet = false,
                    onBrandColorRejected = {},
                ) {
                    Box(modifier = Modifier.width(SCREEN_WIDTH_DP.dp).background(CcTheme.colors.surface)) {
                        CcSampleFrame(sample, Modifier.fillMaxWidth())
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage(path, roborazziOptions = options)
        if (fontScale > 1f) assertNothingClipped()
    }

    private fun assertNothingClipped() {
        val rootWidth = compose.onRoot().fetchSemanticsNode().boundsInRoot.width
        val texts = compose.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        texts.forEach { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            layouts.forEach { layout ->
                // didOverflowWidth is unreliable here: the layout kept for semantics may be measured
                // with wider constraints than the final node. Compare each line with the node instead.
                val widest = (0 until layout.lineCount).maxOf { layout.getLineRight(it) - layout.getLineLeft(it) }
                assertWithMessage("Text '${layout.layoutInput.text}' is wider than its box in $name")
                    .that(widest)
                    .isAtMost(layout.size.width + EDGE_TOLERANCE_PX)
                assertWithMessage("Text '${layout.layoutInput.text}' is cut off at the bottom in $name")
                    .that(layout.didOverflowHeight)
                    .isFalse()
            }
            assertWithMessage("Text node ${node.id} extends past the screen edge in $name")
                .that(node.boundsInRoot.right)
                .isAtMost(rootWidth + EDGE_TOLERANCE_PX)
        }
    }

    private companion object {
        const val SCREEN_WIDTH_DP = 400
        const val EDGE_TOLERANCE_PX = 1f

        const val DARK = "dark"

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}_{1}_{2}_{3}")
        fun cases(): List<Array<Any>> = CcSamples.flatMap { sample ->
            listOf("light", DARK).flatMap { theme ->
                listOf(100, 200).map { percent -> arrayOf<Any>(sample.component, sample.state, theme, percent) }
            }
        }
    }
}
