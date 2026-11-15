package com.clearcapture.clearcapture.designsystem.a11y

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AnnouncementRateLimiterTest {

    private val limiter = AnnouncementRateLimiter(minIntervalMillis = 2_000)

    @Test
    fun `the first text is announced at once`() {
        assertThat(limiter.delayBeforeAnnouncing("Hold steady", nowMillis = 10_000)).isEqualTo(0)
    }

    @Test
    fun `a change within the interval waits for the rest of it`() {
        limiter.onAnnounced("Hold steady", nowMillis = 10_000)
        assertThat(limiter.delayBeforeAnnouncing("Too dark", nowMillis = 10_500)).isEqualTo(1_500)
    }

    @Test
    fun `a change after the interval is announced at once`() {
        limiter.onAnnounced("Hold steady", nowMillis = 10_000)
        assertThat(limiter.delayBeforeAnnouncing("Too dark", nowMillis = 12_000)).isEqualTo(0)
        assertThat(limiter.delayBeforeAnnouncing("Too dark", nowMillis = 30_000)).isEqualTo(0)
    }

    @Test
    fun `the text announced last is not announced again`() {
        limiter.onAnnounced("Ready", nowMillis = 10_000)
        assertThat(limiter.delayBeforeAnnouncing("Ready", nowMillis = 20_000)).isNull()
    }

    @Test
    fun `rapid changes give at most one announcement per interval`() {
        // Simulate a hint changing every 300 ms for 10 s, announcing whenever allowed.
        var now = 0L
        val announcedAt = mutableListOf<Long>()
        var pendingUntil: Long? = null
        var pendingText: String? = null
        var counter = 0
        while (now <= 10_000) {
            if (now % 300 == 0L) {
                val text = "hint ${counter++}"
                val wait = limiter.delayBeforeAnnouncing(text, now)
                if (wait != null) {
                    pendingText = text
                    pendingUntil = now + wait
                }
            }
            val until = pendingUntil
            val text = pendingText
            if (until != null && text != null && now >= until) {
                limiter.onAnnounced(text, now)
                announcedAt += now
                pendingUntil = null
                pendingText = null
            }
            now += 50
        }
        assertThat(announcedAt).isNotEmpty()
        announcedAt.zipWithNext().forEach { (a, b) -> assertThat(b - a).isAtLeast(2_000) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a negative interval is rejected`() {
        AnnouncementRateLimiter(minIntervalMillis = -1)
    }
}
