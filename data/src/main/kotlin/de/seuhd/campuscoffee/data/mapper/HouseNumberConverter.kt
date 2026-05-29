package de.seuhd.campuscoffee.data.mapper

import org.springframework.stereotype.Component

/**
 * Converts a house number string used in the domain (e.g. "21a") to and from the numeric and suffix
 * parts stored on the entity (21, 'a'). Kept separate from the mapper so the parsing is independently
 * unit-testable, including the empty and no-digit inputs that a validated domain object never produces.
 */
@Component
class HouseNumberConverter {
    /** The numeric house number and an optional single suffix character. */
    data class Parts(
        val number: Int?,
        val suffix: Char?
    )

    /**
     * Splits a house number into its numeric part and optional letter suffix (the 'a' in "21a",
     * "21 a", or "21-a"; separators are not retained).
     *
     * @return the parts, both null when the input is null or empty
     * @throws IllegalArgumentException if the input contains no digit
     */
    fun split(houseNumber: String?): Parts {
        if (houseNumber.isNullOrEmpty()) {
            return Parts(null, null)
        }
        val digits = houseNumber.replace(Regex("[^0-9]"), "")
        require(digits.isNotEmpty()) {
            "Invalid house number '$houseNumber': must contain at least one digit."
        }
        val letters = houseNumber.replace(Regex("[^a-zA-Z]"), "")
        val suffix = if (letters.isEmpty()) null else letters[0]
        return Parts(digits.toInt(), suffix)
    }

    /**
     * Merges a numeric house number and optional suffix back into a string.
     *
     * @return the merged string, or null when the numeric part is null
     */
    fun merge(
        number: Int?,
        suffix: Char?
    ): String? {
        if (number == null) {
            return null
        }
        return if (suffix == null) number.toString() else "$number$suffix"
    }
}
