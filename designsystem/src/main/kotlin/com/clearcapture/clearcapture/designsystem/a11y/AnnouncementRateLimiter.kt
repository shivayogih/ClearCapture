package com.clearcapture.clearcapture.designsystem.a11y

/**
 * Decides when a changing text may be announced to a screen reader, so that at most one
 * announcement is made per [minIntervalMillis]. A change that arrives too soon waits; if it is
 * replaced while waiting, only the newest text is announced. Time is passed in, so the class is
 * pure and has no clock of its own.
 */
internal class AnnouncementRateLimiter(private val minIntervalMillis: Long = DEFAULT_INTERVAL_MILLIS) {

    private var lastAnnouncedAt: Long? = null
    private var lastAnnounced: String? = null

    init {
        require(minIntervalMillis >= 0) { "minIntervalMillis must not be negative" }
    }

    /**
     * How long to wait, from [nowMillis], before [text] may be announced: 0 to announce now, or
     * null when [text] is what was announced last and needs no announcement.
     */
    fun delayBeforeAnnouncing(text: String, nowMillis: Long): Long? {
        if (text == lastAnnounced) return null
        val last = lastAnnouncedAt ?: return 0
        return (last + minIntervalMillis - nowMillis).coerceAtLeast(0)
    }

    /** Records that [text] was announced at [nowMillis]. */
    fun onAnnounced(text: String, nowMillis: Long) {
        lastAnnounced = text
        lastAnnouncedAt = nowMillis
    }

    /** Defaults. */
    companion object {
        /** At most one hint announcement every 2 s (DESIGN_SYSTEM.md section 6). */
        const val DEFAULT_INTERVAL_MILLIS: Long = 2_000
    }
}
