package de.seuhd.campuscoffee.data.mapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Tests the house number split and merge logic in isolation, including the empty and no-digit inputs
 * that a validated domain object never reaches.
 */
class HouseNumberConverterTest {

    private final HouseNumberConverter converter = new HouseNumberConverter();

    static Stream<Arguments> houseNumbers() {
        return Stream.of(
                arguments("21a", 21, 'a', "21a"),
                arguments("100", 100, null, "100"),
                arguments("5", 5, null, "5"),
                // the suffix is the first non-digit character, so the "a" after the hyphen is dropped on merge
                arguments("21-a", 21, '-', "21-")
        );
    }

    @ParameterizedTest
    @MethodSource("houseNumbers")
    void splitsAndMerges(String input, int number, Character suffix, String merged) {
        HouseNumberConverter.Parts parts = converter.split(input);

        assertThat(parts.number()).isEqualTo(number);
        assertThat(parts.suffix()).isEqualTo(suffix);
        assertThat(converter.merge(parts.number(), parts.suffix())).isEqualTo(merged);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void splitOfNullOrEmptyYieldsNoParts(String input) {
        HouseNumberConverter.Parts parts = converter.split(input);

        assertThat(parts.number()).isNull();
        assertThat(parts.suffix()).isNull();
    }

    @Test
    void splitWithoutAnyDigitIsRejected() {
        assertThatThrownBy(() -> converter.split("abc")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mergeOfNullNumberIsNull() {
        assertThat(converter.merge(null, 'a')).isNull();
    }
}
