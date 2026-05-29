package de.seuhd.campuscoffee.domain.model.objects

import de.seuhd.campuscoffee.domain.exceptions.ValidationException
import de.seuhd.campuscoffee.domain.model.enums.CampusType
import de.seuhd.campuscoffee.domain.model.enums.PosType
import java.time.LocalDateTime

/**
 * Immutable POS (Point of Sale) domain model. The house number and postal code are validated here to
 * demonstrate validation in the domain model; the remaining fields are validated in the API layer via
 * the DTOs.
 */
data class Pos(
    override val id: Long? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
    val name: String,
    val description: String,
    val type: PosType,
    val campus: CampusType,
    val street: String,
    val houseNumber: String,
    val postalCode: Int,
    val city: String
) : DomainModel<Long> {
    init {
        if (!HOUSE_NUMBER_PATTERN.matches(houseNumber)) {
            throw ValidationException("Invalid house number '$houseNumber'.")
        }
        if (postalCode < MIN_POSTAL_CODE || postalCode > MAX_POSTAL_CODE) {
            throw ValidationException("Invalid postal code '$postalCode'.")
        }
    }

    companion object {
        // see https://github.com/zauberware/postal-codes-json-xml-csv/blob/master/data/DE.zip
        // visible to tests so they derive boundary inputs from these bounds instead of duplicating them
        internal const val MIN_POSTAL_CODE = 1067
        internal const val MAX_POSTAL_CODE = 99998

        // https://de.wikipedia.org/wiki/Hausnummer#Hausnummernerg%C3%A4nzungen
        private val HOUSE_NUMBER_PATTERN = Regex("\\d+[ \\-]?[a-zA-Z]?")
    }
}
