package de.seuhd.campuscoffee.data.mapper

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.NullAndEmptySource
import java.util.stream.Stream

/**
 * Tests the house number split and merge logic in isolation, including the empty and no-digit inputs
 * that a validated domain object never reaches.
 */
class HouseNumberConverterTest {
    private val converter = HouseNumberConverter()

    @ParameterizedTest
    @MethodSource("houseNumbers")
    fun splitsAndMerges(
        input: String,
        number: Int,
        suffix: Char?,
        merged: String
    ) {
        val parts = converter.split(input)

        assertThat(parts.number).isEqualTo(number)
        assertThat(parts.suffix).isEqualTo(suffix)
        assertThat(converter.merge(parts.number, parts.suffix)).isEqualTo(merged)
    }

    @ParameterizedTest
    @NullAndEmptySource
    fun splitOfNullOrEmptyYieldsNoParts(input: String?) {
        val parts = converter.split(input)

        assertThat(parts.number).isNull()
        assertThat(parts.suffix).isNull()
    }

    @Test
    fun splitWithoutAnyDigitIsRejected() {
        assertThatThrownBy { converter.split("abc") }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun mergeOfNullNumberIsNull() {
        assertThat(converter.merge(null, 'a')).isNull()
    }

    companion object {
        @JvmStatic
        fun houseNumbers(): Stream<Arguments> =
            Stream.of(
                arguments("21a", 21, 'a', "21a"),
                arguments("100", 100, null, "100"),
                arguments("5", 5, null, "5"),
                // the letter is the suffix; separators are dropped, so "21-a" and "21 a" normalize to "21a"
                arguments("21-a", 21, 'a', "21a"),
                arguments("21 a", 21, 'a', "21a")
            )
    }
}
