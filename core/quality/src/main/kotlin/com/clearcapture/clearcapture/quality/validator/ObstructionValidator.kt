package com.clearcapture.clearcapture.quality.validator

import com.clearcapture.clearcapture.domain.model.CaptureProfile
import com.clearcapture.clearcapture.domain.model.CheckResult
import com.clearcapture.clearcapture.domain.model.QualityCheck
import com.clearcapture.clearcapture.domain.model.ReasonCode
import com.clearcapture.clearcapture.domain.model.Stage
import com.clearcapture.clearcapture.domain.model.Verdict
import com.clearcapture.clearcapture.opencv.MatPool
import com.clearcapture.clearcapture.opencv.scoped
import com.clearcapture.clearcapture.quality.QualityValidator
import com.clearcapture.clearcapture.quality.ValidationInput
import com.clearcapture.clearcapture.quality.checkResult
import com.clearcapture.clearcapture.quality.guarded
import org.opencv.core.Core
import org.opencv.core.MatOfDouble
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc

/**
 * LLD 5.6. Splits the guide region (the whole frame without a guide) into an 8×6 grid. A cell is low-detail
 * when its Canny edge density is below `cellEdgeMin` and its luminance standard deviation is below `cellStdMin`.
 * The largest 4-connected group of low-detail cells that touches the border of the grid is what a finger or
 * strap over the lens looks like; its share of the grid is compared with the obstruction limits.
 */
internal class ObstructionValidator(private val pool: MatPool) : QualityValidator {
    override val check: QualityCheck = QualityCheck.OBSTRUCTION
    override val stages: Set<Stage> = setOf(Stage.LIVE, Stage.POST_CAPTURE)
    override val liveEveryNthFrame: Int = 3

    override fun validate(input: ValidationInput, profile: CaptureProfile): CheckResult = guarded(check, input.stage) {
        val frame = input.mats
        val t = profile.thresholds
        val lowDetail = pool.scoped {
            val region = roi(frame.gray, frame.guideRect)
            val edges = mat().also { Imgproc.Canny(region, it, CANNY_LOW, CANNY_HIGH) }
            val mean = own(MatOfDouble())
            val stdDev = own(MatOfDouble())
            BooleanArray(COLUMNS * ROWS) { index ->
                val cell = cellRect(index, region.cols(), region.rows())
                pool.scoped {
                    Core.meanStdDev(roi(region, cell), mean, stdDev)
                    val edgeDensity = Core.countNonZero(roi(edges, cell)) / cell.area().coerceAtLeast(1.0)
                    val sigma = stdDev.toArray().firstOrNull() ?: 0.0
                    edgeDensity < t.cellEdgeMin && sigma < t.cellStdMin
                }
            }
        }
        val cells = (COLUMNS * ROWS).toDouble()
        val lowDetailPct = lowDetail.count { it } / cells
        val groupPct = largestBorderGroup(lowDetail) / cells
        val verdict = when {
            groupPct > t.obstructFailPct -> Verdict.FAIL
            groupPct > t.obstructWarnPct -> Verdict.WARNING
            else -> Verdict.PASS
        }
        checkResult(
            check = check,
            verdict = verdict,
            reason = ReasonCode.OBSTRUCTED,
            metrics = mapOf("lowDetailPct" to lowDetailPct, "largestEdgeGroupPct" to groupPct),
            thresholds = mapOf(
                "cellEdgeMin" to t.cellEdgeMin,
                "cellStdMin" to t.cellStdMin,
                "obstructWarnPct" to t.obstructWarnPct,
                "obstructFailPct" to t.obstructFailPct,
            ),
        )
    }

    private fun cellRect(index: Int, width: Int, height: Int): Rect {
        val column = index % COLUMNS
        val row = index / COLUMNS
        val x0 = column * width / COLUMNS
        val y0 = row * height / ROWS
        val x1 = maxOf((column + 1) * width / COLUMNS, x0 + 1)
        val y1 = maxOf((row + 1) * height / ROWS, y0 + 1)
        return Rect(x0, y0, x1 - x0, y1 - y0)
    }

    /** Size of the largest 4-connected group of `true` cells that contains a cell on the grid's border. */
    private fun largestBorderGroup(low: BooleanArray): Int {
        val seen = BooleanArray(low.size)
        var largest = 0
        for (start in low.indices) {
            if (!low[start] || seen[start]) continue
            var size = 0
            var touchesBorder = false
            val stack = ArrayDeque<Int>().apply { addLast(start) }
            seen[start] = true
            while (stack.isNotEmpty()) {
                val cell = stack.removeLast()
                size++
                touchesBorder = touchesBorder || isBorder(cell)
                neighbours(cell).filter { low[it] && !seen[it] }.forEach {
                    seen[it] = true
                    stack.addLast(it)
                }
            }
            if (touchesBorder) largest = maxOf(largest, size)
        }
        return largest
    }

    private fun isBorder(cell: Int): Boolean {
        val column = cell % COLUMNS
        val row = cell / COLUMNS
        return column == 0 || row == 0 || column == COLUMNS - 1 || row == ROWS - 1
    }

    private fun neighbours(cell: Int): List<Int> {
        val column = cell % COLUMNS
        val row = cell / COLUMNS
        return buildList {
            if (column > 0) add(cell - 1)
            if (column < COLUMNS - 1) add(cell + 1)
            if (row > 0) add(cell - COLUMNS)
            if (row < ROWS - 1) add(cell + COLUMNS)
        }
    }

    private companion object {
        const val COLUMNS = 8
        const val ROWS = 6
        const val CANNY_LOW = 80.0
        const val CANNY_HIGH = 160.0
    }
}
