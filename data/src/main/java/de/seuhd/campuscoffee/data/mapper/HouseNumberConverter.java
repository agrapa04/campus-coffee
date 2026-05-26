package de.seuhd.campuscoffee.data.mapper;

import org.springframework.stereotype.Component;

/**
 * Converts a house number string used in the domain (e.g. "21a") to and from the numeric and suffix
 * parts stored on the entity (21, 'a'). Kept separate from the mapper so the parsing is independently
 * unit-testable, including the empty and no-digit inputs that a validated domain object never produces.
 */
@Component
public class HouseNumberConverter {

    /** The numeric house number and an optional single suffix character. */
    public record Parts(Integer number, Character suffix) {
    }

    /**
     * Splits a house number string into its numeric part and optional suffix character. The suffix is
     * the first non-digit character, so "21-a" yields the suffix '-'.
     *
     * @param houseNumber the house number string; may be null or empty
     * @return the parts, both null when the input is null or empty
     * @throws IllegalArgumentException if the input contains no digit
     */
    public Parts split(String houseNumber) {
        if (houseNumber == null || houseNumber.isEmpty()) {
            return new Parts(null, null);
        }
        String digits = houseNumber.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            throw new IllegalArgumentException(
                    "Invalid house number '" + houseNumber + "': must contain at least one digit.");
        }
        String nonDigits = houseNumber.replaceAll("[0-9]", "");
        Character suffix = nonDigits.isEmpty() ? null : nonDigits.charAt(0);
        return new Parts(Integer.parseInt(digits), suffix);
    }

    /**
     * Merges a numeric house number and optional suffix back into a string.
     *
     * @param number the numeric part; may be null
     * @param suffix the suffix character; may be null
     * @return the merged string, or null when the numeric part is null
     */
    public String merge(Integer number, Character suffix) {
        if (number == null) {
            return null;
        }
        return suffix == null ? number.toString() : number.toString() + suffix;
    }
}
