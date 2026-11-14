package com.clearcapture.clearcapture.ui.text

import com.clearcapture.clearcapture.domain.model.ReasonCode
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReasonTextTest {

    @Test
    fun `every reason code has its own title and advice`() {
        val pairs = ReasonCode.entries.map(ReasonText::of)
        assertThat(pairs.map { it.first }.toSet()).hasSize(ReasonCode.entries.size)
        assertThat(pairs.map { it.second }.toSet()).hasSize(ReasonCode.entries.size)
    }
}
