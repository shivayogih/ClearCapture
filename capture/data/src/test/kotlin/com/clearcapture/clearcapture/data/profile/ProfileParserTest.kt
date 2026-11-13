package com.clearcapture.clearcapture.data.profile

import com.clearcapture.clearcapture.common.Outcome
import com.clearcapture.clearcapture.domain.model.BlurLimits
import com.clearcapture.clearcapture.domain.model.CameraTraits
import com.clearcapture.clearcapture.domain.model.CaptureProfile
import com.clearcapture.clearcapture.domain.model.GuideShape
import com.clearcapture.clearcapture.domain.model.ProfileResolver
import com.clearcapture.clearcapture.domain.model.QualityCheck
import com.clearcapture.clearcapture.domain.model.TestProfiles
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class ProfileParserTest {

    private val bundled: String = File("src/main/assets/clearcapture/profiles.json").readText()

    private fun parseOk(text: String): List<CaptureProfile> {
        val outcome = ProfileParser.parse(text)
        assertThat(outcome).isInstanceOf(Outcome.Success::class.java)
        return (outcome as Outcome.Success).value
    }

    private fun parseError(text: String): String {
        val outcome = ProfileParser.parse(text)
        assertThat(outcome).isInstanceOf(Outcome.Failure::class.java)
        return (outcome as Outcome.Failure).error
    }

    @Test
    fun `bundled file has the four profiles in order`() {
        assertThat(parseOk(bundled).map { it.id }).containsExactly("container", "person", "vehicle", "general").inOrder()
    }

    @Test
    fun `container inherits every default`() {
        val container = parseOk(bundled).first { it.id == "container" }
        assertThat(container.thresholds).isEqualTo(TestProfiles.defaultThresholds)
        assertThat(container.location).isEqualTo(TestProfiles.defaultLocation)
        assertThat(container.hintHoldMillis).isEqualTo(400)
        assertThat(container.allowAcceptOnWarning).isTrue()
        assertThat(container.allowOverrideOnFail).isFalse()
        assertThat(container.guideShape).isEqualTo(GuideShape.CONTAINER)
    }

    @Test
    fun `profile overrides only what it lists`() {
        val profiles = parseOk(bundled)
        val person = profiles.first { it.id == "person" }
        val vehicle = profiles.first { it.id == "vehicle" }
        assertThat(person.thresholds).isEqualTo(TestProfiles.defaultThresholds.copy(coverageMin = 0.35))
        assertThat(vehicle.thresholds).isEqualTo(TestProfiles.defaultThresholds.copy(glareFailPct = 0.10))
        assertThat(vehicle.warningOnlyChecks).containsExactly(QualityCheck.FRAMING, QualityCheck.OBSTRUCTION, QualityCheck.GLARE)
    }

    @Test
    fun `general has no guide`() {
        val general = parseOk(bundled).first { it.id == "general" }
        assertThat(general.guide).isNull()
        assertThat(general.guideShape).isEqualTo(GuideShape.NONE)
    }

    @Test
    fun `device overrides are attached unapplied and resolve on fixed focus cameras`() {
        val container = parseOk(bundled).first { it.id == "container" }
        assertThat(container.thresholds.finalBlur).isEqualTo(BlurLimits(120.0, 180.0))

        val fixedFocus = ProfileResolver.resolve(container, CameraTraits(fixedFocus = true, hasFlash = false, hardwareLevel = "LIMITED"))
        val autoFocus = ProfileResolver.resolve(container, CameraTraits(fixedFocus = false, hasFlash = false, hardwareLevel = "LIMITED"))

        assertThat(fixedFocus.thresholds.finalBlur).isEqualTo(BlurLimits(90.0, 140.0))
        assertThat(autoFocus.thresholds.finalBlur).isEqualTo(BlurLimits(120.0, 180.0))
    }

    @Test
    fun `location required adds LOCATION to required checks even if not listed`() {
        val text = bundled.replace(
            "\"requiredChecks\": [\"BLUR\", \"EXPOSURE\", \"RESOLUTION\", \"OBSTRUCTION\", \"LOCATION\"]",
            "\"requiredChecks\": [\"BLUR\", \"EXPOSURE\", \"RESOLUTION\", \"OBSTRUCTION\"]",
        )
        val general = parseOk(text).first { it.id == "general" }
        assertThat(general.requiredChecks).contains(QualityCheck.LOCATION)
    }

    @Test
    fun `location not required removes LOCATION from required checks`() {
        val text = bundled.replace(
            "\"id\": \"general\",",
            "\"id\": \"general\", \"location\": { \"required\": false },",
        )
        val general = parseOk(text).first { it.id == "general" }
        assertThat(general.location.required).isFalse()
        assertThat(general.location.maxAgeSeconds).isEqualTo(60)
        assertThat(general.requiredChecks).doesNotContain(QualityCheck.LOCATION)
    }

    @Test
    fun `unknown schema version is rejected`() {
        assertThat(parseError(bundled.replace("\"schemaVersion\": 1", "\"schemaVersion\": 99"))).contains("schemaVersion")
    }

    @Test
    fun `malformed json is rejected without throwing`() {
        assertThat(parseError("{ not json")).contains("malformed")
    }

    @Test
    fun `unknown check name is rejected`() {
        assertThat(parseError(bundled.replace("\"GLARE\", \"FRAMING\"", "\"SHINY\", \"FRAMING\""))).contains("SHINY")
    }

    @Test
    fun `guide outside the frame is rejected`() {
        assertThat(parseError(bundled.replace("\"left\": 0.18", "\"left\": 1.5"))).contains("container")
    }

    @Test
    fun `guide shape without a guide is rejected`() {
        assertThat(parseError(bundled.replace("\"guideShape\": \"NONE\"", "\"guideShape\": \"CONTAINER\""))).contains("general")
    }

    @Test
    fun `inconsistent thresholds are rejected`() {
        assertThat(parseError(bundled.replace("\"glareFailPct\": 0.10", "\"glareFailPct\": 0.01"))).contains("glare")
    }

    @Test
    fun `missing default threshold is rejected`() {
        assertThat(parseError(bundled.replace("\"lumaMin\": 60, ", ""))).contains("lumaMin")
    }

    @Test
    fun `duplicate ids are rejected`() {
        assertThat(parseError(bundled.replace("\"id\": \"person\"", "\"id\": \"container\""))).contains("Duplicate")
    }
}
