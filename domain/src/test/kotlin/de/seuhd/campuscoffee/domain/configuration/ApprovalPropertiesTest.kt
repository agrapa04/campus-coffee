package de.seuhd.campuscoffee.domain.configuration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Tests the bounds check in [ApprovalProperties]: an invalid minimum approval count must fail at
 * construction (and thus at property binding on startup), not later during an approval request.
 */
class ApprovalPropertiesTest {
    @Test
    fun `the constructor accepts a positive minimum approval count`() {
        assertThat(ApprovalProperties(1).minCount).isEqualTo(1)
    }

    @ParameterizedTest
    @ValueSource(ints = [0, -1])
    fun `the constructor throws IllegalArgumentException for a minimum approval count below one`(minCount: Int) {
        assertThrows<IllegalArgumentException> { ApprovalProperties(minCount) }
    }
}
