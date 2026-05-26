package de.seuhd.campuscoffee.domain.model.objects;

import de.seuhd.campuscoffee.domain.exceptions.ValidationException;
import de.seuhd.campuscoffee.domain.model.enums.CampusType;
import de.seuhd.campuscoffee.domain.model.enums.PosType;
import lombok.Builder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Domain record that stores the POS (Point of Sale) metadata.
 * This is an immutable value object - use the builder or toBuilder() to create modified copies.
 * Records provide automatic implementations of equals(), hashCode(), toString(), and accessors.
 * <p>
 * We validate the fields in the API layer based on the DTOs, so no validation annotations are needed here.
 *
 * @param id          the unique identifier; null when the POS has not been created yet
 * @param createdAt   timestamp set on POS creation
 * @param updatedAt   timestamp set on POS creation and update
 * @param name        the name of the POS
 * @param description a description of the POS
 * @param type        the type of POS (café, bakery, etc.)
 * @param campus      the campus location
 * @param street      street name
 * @param houseNumber house number (may include suffix such as "21a")
 * @param postalCode  postal code
 * @param city        city name
 */
@Builder(toBuilder = true)
public record Pos (
        @Nullable Long id, // null when the POS has not been created yet
        @Nullable LocalDateTime createdAt, // set on POS creation
        @Nullable LocalDateTime updatedAt, // set on POS creation and update
        @NonNull String name,
        @NonNull String description,
        @NonNull PosType type,
        @NonNull CampusType campus,
        @NonNull String street,
        @NonNull String houseNumber,
        @NonNull Integer postalCode,
        @NonNull String city
) implements DomainModel<Long> {
    // see https://github.com/zauberware/postal-codes-json-xml-csv/blob/master/data/DE.zip
    // package-private so tests derive their boundary inputs from these bounds instead of duplicating them
    static final int MIN_POSTAL_CODE = 1067;
    static final int MAX_POSTAL_CODE = 99998;
    // https://de.wikipedia.org/wiki/Hausnummer#Hausnummernerg%C3%A4nzungen
    private static final Pattern HOUSE_NUMBER_PATTERN = Pattern.compile("\\d+[ \\-]?[a-zA-Z]?");

    // This is a record custom constructor that validates the POS fields.
    // The custom constructor is called before the record constructor.
    public Pos {
        validateHouseNumber(houseNumber);
        validatePostalCode(postalCode);
    }

    @Override
    public Long getId() {
        return id;
    }

    //TODO: The validatePostalCode and validateHouseNumber methods could be replaced by bean validation
    // annotations. This code is to demonstrate testing package-private methods and also to show an alternative
    // to bean validation.

    /**
     * Validates that the POS postal code is within the allowed range of German postal codes.
     *
     * @param postalCode the postal code to validate
     * @throws ValidationException if the POS postal code is less than {@code MIN_POSTAL_CODE}
     *                             or greater than {@code MAX_POSTAL_CODE}
     */
    void validatePostalCode(Integer postalCode) {
        Objects.requireNonNull(postalCode);
        if (postalCode < MIN_POSTAL_CODE || postalCode > MAX_POSTAL_CODE) {
            throw new ValidationException("Invalid postal code '" + postalCode + "'.");
        }
    }

    /**
     * Validates the provided house number based on a predefined patter
     * that accounts for standard German house numbering conventions.
     *
     * @param houseNumber the house number to validate
     * @throws ValidationException if the POS house number does not match the valid pattern
     */
    void validateHouseNumber(String houseNumber) {
        Objects.requireNonNull(houseNumber);
        if (!HOUSE_NUMBER_PATTERN.matcher(houseNumber).matches()) {
            throw new ValidationException("Invalid house number '" + houseNumber + "'.");
        }
    }

}
