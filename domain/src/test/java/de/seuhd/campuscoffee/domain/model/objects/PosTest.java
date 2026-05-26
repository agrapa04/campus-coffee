package de.seuhd.campuscoffee.domain.model.objects;

import de.seuhd.campuscoffee.domain.exceptions.ValidationException;
import de.seuhd.campuscoffee.domain.tests.TestFixtures;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests the validation in the {@link Pos} record constructor: postal code range and house number pattern.
 */
public class PosTest {

    @ParameterizedTest
    // the inclusive bounds and a regular code in between; bounds come from Pos, not hard-coded here
    @ValueSource(ints = {Pos.MIN_POSTAL_CODE, Pos.MAX_POSTAL_CODE, 69117})
    void validPostalCodesAreAccepted(int postalCode) {
        assertDoesNotThrow(() -> posWithPostalCode(postalCode));
    }

    @ParameterizedTest
    // just below the lower bound, just above the upper bound, and zero
    @ValueSource(ints = {Pos.MIN_POSTAL_CODE - 1, Pos.MAX_POSTAL_CODE + 1, 0})
    void postalCodesOutsideTheRangeAreRejected(int postalCode) {
        assertThrows(ValidationException.class, () -> posWithPostalCode(postalCode));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "100", "21a", "21-a", "21 a"})
    void validHouseNumbersAreAccepted(String houseNumber) {
        assertDoesNotThrow(() -> posWithHouseNumber(houseNumber));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "abc", "21ab", "-"}) // no digit, no digit, two suffix letters, no digit
    void invalidHouseNumbersAreRejected(String houseNumber) {
        assertThrows(ValidationException.class, () -> posWithHouseNumber(houseNumber));
    }

    private Pos posWithPostalCode(int postalCode) {
        return TestFixtures.getPosFixtures().getFirst().toBuilder().postalCode(postalCode).build();
    }

    private Pos posWithHouseNumber(String houseNumber) {
        return TestFixtures.getPosFixtures().getFirst().toBuilder().houseNumber(houseNumber).build();
    }
}
