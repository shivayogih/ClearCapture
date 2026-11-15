package com.clearcapture.clearcapture.opencv

import org.opencv.core.Mat
import org.opencv.core.Rect
import java.util.concurrent.atomic.AtomicInteger

/**
 * A small pool of reusable [Mat]s with deterministic release.
 *
 * Every mat handed out by [acquire] or registered with [adopt] is counted in [outstanding] until it is
 * returned with [recycle] or [discard], so tests can assert that the balance is zero after each call.
 * Small mats (the 640×480 live working set) are kept for reuse, up to [maxPooled] of them; larger ones,
 * such as the post-capture working set, are released at once so the pool never holds much native memory.
 * Thread-safe.
 */
public class MatPool(
    private val maxPooled: Int = DEFAULT_MAX_POOLED,
    private val maxPooledBytes: Long = DEFAULT_MAX_POOLED_BYTES,
) {
    private val lock = Any()
    private val free = ArrayDeque<Mat>()
    private val outstandingCount = AtomicInteger()

    /** Mats handed out and not yet returned. Zero whenever no analysis is running. */
    public val outstanding: Int get() = outstandingCount.get()

    /** Mats currently kept for reuse. */
    public val pooled: Int get() = synchronized(lock) { free.size }

    /** A mat to be used as an output; its size, type and content are unspecified. Return it with [recycle]. */
    public fun acquire(): Mat {
        outstandingCount.incrementAndGet()
        return synchronized(lock) { free.removeLastOrNull() } ?: Mat()
    }

    /** Returns a mat from [acquire]. It is kept for reuse when small enough, otherwise released. */
    public fun recycle(mat: Mat) {
        outstandingCount.decrementAndGet()
        val small = !mat.isSubmatrix && mat.total() * mat.elemSize() <= maxPooledBytes
        val kept = small && synchronized(lock) { if (free.size < maxPooled) free.addLast(mat).let { true } else false }
        if (!kept) mat.release()
    }

    /** Counts a mat created elsewhere (a sub-matrix, an OpenCV output list entry) until [discard] releases it. */
    public fun <T : Mat> adopt(mat: T): T {
        outstandingCount.incrementAndGet()
        return mat
    }

    /** Releases a mat registered with [adopt]. It is never reused. */
    public fun discard(mat: Mat) {
        outstandingCount.decrementAndGet()
        mat.release()
    }

    /** Releases every mat kept for reuse. Call when the capture flow ends. */
    public fun clear() {
        val drained = synchronized(lock) { free.toList().also { free.clear() } }
        drained.forEach(Mat::release)
    }

    private companion object {
        const val DEFAULT_MAX_POOLED = 16
        const val DEFAULT_MAX_POOLED_BYTES = 2L * 1024 * 1024
    }
}

/**
 * Mats used by one computation. Everything taken through the scope is returned to the pool by [close],
 * whatever happens in between. Not thread-safe; use one scope per call.
 */
public class MatScope(private val pool: MatPool) : AutoCloseable {
    private val acquired = ArrayList<Mat>(INITIAL_CAPACITY)
    private val adopted = ArrayList<Mat>(INITIAL_CAPACITY)

    /** A working mat from the pool. */
    public fun mat(): Mat = pool.acquire().also(acquired::add)

    /** Takes ownership of a mat created elsewhere; it is released on [close]. */
    public fun <T : Mat> own(mat: T): T = pool.adopt(mat).also(adopted::add)

    /** A view of [rect] inside [parent]; it shares the parent's pixels and is released on [close]. */
    public fun roi(parent: Mat, rect: Rect): Mat = own(parent.submat(rect))

    override fun close() {
        acquired.forEach(pool::recycle)
        adopted.forEach(pool::discard)
        acquired.clear()
        adopted.clear()
    }

    private companion object {
        const val INITIAL_CAPACITY = 8
    }
}

/** Runs [block] with a [MatScope] that returns every mat it handed out, also when [block] throws. */
public inline fun <R> MatPool.scoped(block: MatScope.() -> R): R = MatScope(this).use { it.block() }

/**
 * Acquires a mat, lets [fill] write it and hands it to the caller, who must [MatPool.recycle] it.
 * If [fill] throws, the mat goes back to the pool before the exception propagates.
 */
public inline fun MatPool.produce(fill: (Mat) -> Unit): Mat {
    val out = acquire()
    var filled = false
    try {
        fill(out)
        filled = true
    } finally {
        if (!filled) recycle(out)
    }
    return out
}
