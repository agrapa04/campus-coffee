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
    // the inclusive bounds and a regular code in between; bounds come from Pos, not hard-coded here
    @ValueSource(ints = [Pos.MIN_POSTAL_CODE, Pos.MAX_POSTAL_CODE, 69117])
    fun validPostalCodesAreAccepted(postalCode: Int) {
        assertDoesNotThrow { posWithPostalCode(postalCode) }
    }

    @ParameterizedTest
    // just below the lower bound, just above the upper bound, and zero
    @ValueSource(ints = [Pos.MIN_POSTAL_CODE - 1, Pos.MAX_POSTAL_CODE + 1, 0])
    fun postalCodesOutsideTheRangeAreRejected(postalCode: Int) {
        assertThrows<ValidationException> { posWithPostalCode(postalCode) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["1", "100", "21a", "21-a", "21 a"])
    fun validHouseNumbersAreAccepted(houseNumber: String) {
        assertDoesNotThrow { posWithHouseNumber(houseNumber) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["a", "abc", "21ab", "-"]) // no digit, no digit, two suffix letters, no digit
    fun invalidHouseNumbersAreRejected(houseNumber: String) {
        assertThrows<ValidationException> { posWithHouseNumber(houseNumber) }
    }

    private fun posWithPostalCode(postalCode: Int): Pos =
        TestFixtures.getPosFixtures().first().copy(postalCode = postalCode)

    private fun posWithHouseNumber(houseNumber: String): Pos =
        TestFixtures.getPosFixtures().first().copy(houseNumber = houseNumber)
}
