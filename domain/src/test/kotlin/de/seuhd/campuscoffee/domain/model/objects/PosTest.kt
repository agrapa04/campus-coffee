package de.seuhd.campuscoffee.domain.model.objects

import de.seuhd.campuscoffee.domain.exceptions.ValidationException
import de.seuhd.campuscoffee.domain.tests.TestFixtures
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Tests the validation in the [Pos] constructor: postal code range and house number pattern.
 */
class PosTest {
    @ParameterizedTest
    // the inclusive bounds, a regular code in between, and a leading-zero code; bounds come from Pos
    @ValueSource(strings = [Pos.MIN_POSTAL_CODE, Pos.MAX_POSTAL_CODE, "69117", "01069"])
    fun `the Pos constructor accepts valid postal codes`(postalCode: String) {
        assertDoesNotThrow { posWithPostalCode(postalCode) }
    }

    @ParameterizedTest
    // just below "01067", just above "99998", too short (also a code with its leading zero dropped),
    // too long, and non-digit characters
    @ValueSource(strings = ["01066", "99999", "1067", "691170", "6911a", "69 17"])
    fun `the Pos constructor rejects invalid postal codes with ValidationException`(postalCode: String) {
        assertThrows<ValidationException> { posWithPostalCode(postalCode) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["1", "100", "21a", "21-a", "21 a"])
    fun `the Pos constructor accepts valid house numbers`(houseNumber: String) {
        assertDoesNotThrow { posWithHouseNumber(houseNumber) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["a", "abc", "21ab", "-"]) // no digit, no digit, two suffix letters, no digit
    fun `the Pos constructor rejects invalid house numbers with ValidationException`(houseNumber: String) {
        assertThrows<ValidationException> { posWithHouseNumber(houseNumber) }
    }

    private fun posWithPostalCode(postalCode: String): Pos = TestFixtures.anyPos().copy(postalCode = postalCode)

    private fun posWithHouseNumber(houseNumber: String): Pos = TestFixtures.anyPos().copy(houseNumber = houseNumber)
}
