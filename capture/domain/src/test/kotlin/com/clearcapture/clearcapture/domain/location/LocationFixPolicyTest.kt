package com.clearcapture.clearcapture.domain.location

import com.clearcapture.clearcapture.domain.model.LocationFix
import com.clearcapture.clearcapture.domain.model.LocationStatus
import com.clearcapture.clearcapture.domain.model.ReasonCode
import com.clearcapture.clearcapture.domain.model.TestProfiles
import com.clearcapture.clearcapture.domain.model.Verdict
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Every transition of the LLD 8.1 state diagram that the policy decides, with a fake clock. */
class LocationFixPolicyTest {

    private val policy = TestProfiles.defaultLocation // 60 s, 50 m
    private val t0 = 1_000_000L
    private fun fix(accuracy: Float = 5f, time: Long = t0, mock: Boolean = false) =
        LocationFix(51.5, -0.12, 30.0, accuracy, time, "gps", mock)

    @Test
    fun `acquiring to ready with a fresh accurate fix`() {
        assertThat(LocationFixPolicy.evaluate(fix(), policy, t0 + 1_000)).isInstanceOf(LocationStatus.Ready::class.java)
    }

    @Test
    fun `acquiring to inaccurate when accuracy is too low`() {
        assertThat(LocationFixPolicy.evaluate(fix(accuracy = 51f), policy, t0)).isInstanceOf(LocationStatus.Inaccurate::class.java)
    }

    @Test
    fun `inaccurate to ready with a better fix`() {
        assertThat(LocationFixPolicy.evaluate(fix(accuracy = 50f), policy, t0)).isInstanceOf(LocationStatus.Ready::class.java)
    }

    @Test
    fun `ready to stale when no update arrives for max age`() {
        assertThat(LocationFixPolicy.evaluate(fix(), policy, t0 + 60_000)).isInstanceOf(LocationStatus.Ready::class.java)
        assertThat(LocationFixPolicy.evaluate(fix(), policy, t0 + 60_001)).isInstanceOf(LocationStatus.Stale::class.java)
    }

    @Test
    fun `stale to ready with a new fix`() {
        assertThat(LocationFixPolicy.evaluate(fix(time = t0 + 60_000), policy, t0 + 61_000)).isInstanceOf(LocationStatus.Ready::class.java)
    }

    @Test
    fun `capture is blocked without permission, with location off and while acquiring`() {
        for (status in listOf(LocationStatus.PermissionMissing, LocationStatus.ProviderDisabled, LocationStatus.Acquiring)) {
            assertThat(LocationFixPolicy.allowsCapture(status, policy)).isFalse()
        }
        assertThat(LocationFixPolicy.blockReason(LocationStatus.PermissionMissing, policy)).isEqualTo(ReasonCode.LOCATION_PERMISSION)
        assertThat(LocationFixPolicy.blockReason(LocationStatus.ProviderDisabled, policy)).isEqualTo(ReasonCode.LOCATION_DISABLED)
        assertThat(LocationFixPolicy.blockReason(LocationStatus.Acquiring, policy)).isEqualTo(ReasonCode.LOCATION_NO_FIX)
    }

    @Test
    fun `stale and inaccurate allow capture unless the policy fails on them`() {
        assertThat(LocationFixPolicy.allowsCapture(LocationStatus.Stale(fix()), policy)).isTrue()
        assertThat(LocationFixPolicy.allowsCapture(LocationStatus.Inaccurate(fix()), policy)).isTrue()
        val strict = policy.copy(failOnStale = true, failOnInaccurate = true)
        assertThat(LocationFixPolicy.blockReason(LocationStatus.Stale(fix()), strict)).isEqualTo(ReasonCode.LOCATION_STALE)
        assertThat(LocationFixPolicy.blockReason(LocationStatus.Inaccurate(fix()), strict)).isEqualTo(ReasonCode.LOCATION_INACCURATE)
    }

    @Test
    fun `a profile that needs no location never blocks`() {
        val optional = policy.copy(required = false)
        assertThat(LocationFixPolicy.allowsCapture(LocationStatus.PermissionMissing, optional)).isTrue()
        assertThat(LocationFixPolicy.allowsCapture(LocationStatus.NotRequired, policy)).isTrue()
    }

    @Test
    fun `check result passes for a good fix with tags`() {
        val result = LocationFixPolicy.checkResult(fix(), policy, t0 + 2_000, tagsPresent = true)
        assertThat(result.verdict).isEqualTo(Verdict.PASS)
        assertThat(result.reason).isNull()
        assertThat(result.metrics["ageSeconds"]).isEqualTo(2.0)
    }

    @Test
    fun `check result fails when tags are missing after saving`() {
        val result = LocationFixPolicy.checkResult(fix(), policy, t0, tagsPresent = false)
        assertThat(result.verdict).isEqualTo(Verdict.FAIL)
        assertThat(result.reason).isEqualTo(ReasonCode.LOCATION_TAGS_MISSING)
    }

    @Test
    fun `check result warns on stale or inaccurate, fails when the policy says so`() {
        assertThat(LocationFixPolicy.checkResult(fix(accuracy = 80f), policy, t0, true).verdict).isEqualTo(Verdict.WARNING)
        val strict = policy.copy(failOnInaccurate = true)
        val result = LocationFixPolicy.checkResult(fix(accuracy = 80f), strict, t0, true)
        assertThat(result.verdict).isEqualTo(Verdict.FAIL)
        assertThat(result.reason).isEqualTo(ReasonCode.LOCATION_INACCURATE)
    }

    @Test
    fun `mock location is flagged for review`() {
        val result = LocationFixPolicy.checkResult(fix(mock = true), policy, t0, true)
        assertThat(result.verdict).isEqualTo(Verdict.WARNING)
        assertThat(result.reason).isEqualTo(ReasonCode.LOCATION_MOCK)
    }

    @Test
    fun `no fix at all fails`() {
        assertThat(LocationFixPolicy.checkResult(null, policy, t0, false).reason).isEqualTo(ReasonCode.LOCATION_NO_FIX)
    }
}
