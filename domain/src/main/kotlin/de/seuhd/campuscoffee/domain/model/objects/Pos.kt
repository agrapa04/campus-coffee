package de.seuhd.campuscoffee.domain.model.objects

import de.seuhd.campuscoffee.domain.exceptions.ValidationException
import de.seuhd.campuscoffee.domain.model.enums.CampusType
import de.seuhd.campuscoffee.domain.model.enums.PosType
import java.time.LocalDateTime
import java.util.UUID

/**
 * Immutable POS (Point of Sale) domain model. The house number and postal code are validated here to
 * demonstrate validation in the domain model; the remaining fields are validated in the API layer via
 * the DTOs.
 */
data class Pos(
    override val id: UUID? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
    val version: Long? = null,
    val name: String,
    val description: String,
    val type: PosType,
    val campus: CampusType,
    val street: String,
    val houseNumber: String,
    val postalCode: String,
    val city: String
) : DomainModel<UUID> {
    init {
        if (!HOUSE_NUMBER_PATTERN.matches(houseNumber)) {
            throw ValidationException("Invalid house number '$houseNumber'.")
        }
        // a German postal code is a fixed five-digit string (leading zeros are significant, e.g.
        // "01067"), so the range check can compare the strings directly
        if (!POSTAL_CODE_PATTERN.matches(postalCode) ||
            postalCode < MIN_POSTAL_CODE ||
            postalCode > MAX_POSTAL_CODE
        ) {
            throw ValidationException("Invalid postal code '$postalCode'.")
        }
    }

    companion object {
        // see https://github.com/zauberware/postal-codes-json-xml-csv/blob/master/data/DE.zip
        // visible to tests so they derive boundary inputs from these bounds instead of duplicating them
        internal const val MIN_POSTAL_CODE = "01067"
        internal const val MAX_POSTAL_CODE = "99998"

        private val POSTAL_CODE_PATTERN = Regex("\\d{5}")

        // https://de.wikipedia.org/wiki/Hausnummer#Hausnummernerg%C3%A4nzungen
        // A number with no leading zero and an optional single letter suffix, no separator. The numeric and
        // suffix parts are stored in separate columns, so a leading zero ("007") or a separator ("21-a")
        // would be silently dropped on the persistence round-trip; rejecting them here keeps a stored house
        // number identical to the one the client sent.
        private val HOUSE_NUMBER_PATTERN = Regex("[1-9]\\d*[a-zA-Z]?")
    }
}
