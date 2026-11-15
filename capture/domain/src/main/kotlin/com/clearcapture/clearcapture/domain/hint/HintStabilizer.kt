package com.clearcapture.clearcapture.domain.hint

/**
 * Keeps hints from flickering (LLD 3.4): a candidate replaces the current hint only after it has been the
 * top candidate continuously for [holdMillis]. Changing to Ready uses the same hold. Not thread-safe; one per screen.
 */
class HintStabilizer(private val holdMillis: Long) {

    /** The hint currently shown. */
    var current: Hint = Hint(HintKind.NONE, blocking = false)
        private set

    private var candidate: Hint? = null
    private var candidateSince: Long = 0

    /** Offers the latest [next] hint at [nowMillis] and returns the hint to show. */
    fun offer(next: Hint, nowMillis: Long): Hint {
        when {
            next == current -> candidate = null
            next != candidate -> {
                candidate = next
                candidateSince = nowMillis
            }
            nowMillis - candidateSince >= holdMillis -> {
                current = next
                candidate = null
            }
        }
        return current
    }
}
