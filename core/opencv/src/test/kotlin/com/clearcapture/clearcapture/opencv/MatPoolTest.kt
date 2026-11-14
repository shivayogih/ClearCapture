package com.clearcapture.clearcapture.opencv

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect

class MatPoolTest {
    init {
        DesktopNatives.load()
    }

    @Test
    fun `acquire and recycle keep the balance and reuse small mats`() {
        val pool = MatPool(maxPooled = 2)
        val first = pool.acquire().apply { create(10, 10, CvType.CV_8UC1) }
        assertThat(pool.outstanding).isEqualTo(1)
        pool.recycle(first)
        assertThat(pool.outstanding).isEqualTo(0)
        assertThat(pool.pooled).isEqualTo(1)
        assertThat(pool.acquire()).isSameInstanceAs(first)
    }

    @Test
    fun `large mats are released instead of pooled`() {
        val pool = MatPool(maxPooledBytes = 1024)
        val large = pool.acquire().apply { create(100, 100, CvType.CV_8UC1) }
        pool.recycle(large)
        assertThat(pool.pooled).isEqualTo(0)
        assertThat(large.empty()).isTrue()
    }

    @Test
    fun `the pool keeps at most maxPooled mats`() {
        val pool = MatPool(maxPooled = 2)
        val mats = List(4) { pool.acquire() }
        mats.forEach(pool::recycle)
        assertThat(pool.pooled).isEqualTo(2)
        pool.clear()
        assertThat(pool.pooled).isEqualTo(0)
    }

    @Test
    fun `a scope returns everything, also when its block throws`() {
        val pool = MatPool()
        val parent = Mat(20, 20, CvType.CV_8UC1)
        assertThrows(IllegalStateException::class.java) {
            pool.scoped {
                mat()
                roi(parent, Rect(0, 0, 5, 5))
                own(Mat())
                assertThat(pool.outstanding).isEqualTo(3)
                error("boom")
            }
        }
        assertThat(pool.outstanding).isEqualTo(0)
        // Sub-matrices are released, never pooled.
        assertThat(pool.pooled).isEqualTo(1)
        parent.release()
    }

    @Test
    fun `produce hands back a filled mat and returns it to the pool when filling fails`() {
        val pool = MatPool()
        val made = pool.produce { it.create(3, 3, CvType.CV_8UC1) }
        assertThat(pool.outstanding).isEqualTo(1)
        pool.recycle(made)
        assertThrows(IllegalArgumentException::class.java) { pool.produce { throw IllegalArgumentException("x") } }
        assertThat(pool.outstanding).isEqualTo(0)
    }
}
