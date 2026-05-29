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
    val city: String,
) : DomainModel<Long> {

    init {
        if (!HOUSE_NUMBER_PATTERN.matches(houseNumber)) {
            throw ValidationException("Invalid house number '$houseNumber'.")
        }
        if (postalCode < MIN_POSTAL_CODE || postalCode > MAX_POSTAL_CODE) {
            throw ValidationException("Invalid postal code '$postalCode'.")
        }
    }

    // --- temporary bridges so the still-Java tests keep compiling; removed once the tests are Kotlin ---
    fun id() = id
    fun createdAt() = createdAt
    fun updatedAt() = updatedAt
    fun name() = name
    fun description() = description
    fun type() = type
    fun campus() = campus
    fun street() = street
    fun houseNumber() = houseNumber
    fun postalCode() = postalCode
    fun city() = city

    fun toBuilder() = Builder()
        .id(id).createdAt(createdAt).updatedAt(updatedAt)
        .name(name).description(description).type(type).campus(campus)
        .street(street).houseNumber(houseNumber).postalCode(postalCode).city(city)

    class Builder {
        private var id: Long? = null
        private var createdAt: LocalDateTime? = null
        private var updatedAt: LocalDateTime? = null
        private var name: String? = null
        private var description: String? = null
        private var type: PosType? = null
        private var campus: CampusType? = null
        private var street: String? = null
        private var houseNumber: String? = null
        private var postalCode: Int? = null
        private var city: String? = null

        fun id(v: Long?) = apply { id = v }
        fun createdAt(v: LocalDateTime?) = apply { createdAt = v }
        fun updatedAt(v: LocalDateTime?) = apply { updatedAt = v }
        fun name(v: String) = apply { name = v }
        fun description(v: String) = apply { description = v }
        fun type(v: PosType) = apply { type = v }
        fun campus(v: CampusType) = apply { campus = v }
        fun street(v: String) = apply { street = v }
        fun houseNumber(v: String) = apply { houseNumber = v }
        fun postalCode(v: Int) = apply { postalCode = v }
        fun city(v: String) = apply { city = v }

        fun build() = Pos(
            id, createdAt, updatedAt, name!!, description!!, type!!, campus!!,
            street!!, houseNumber!!, postalCode!!, city!!,
        )
    }

    companion object {
        @JvmStatic
        fun builder() = Builder()

        // see https://github.com/zauberware/postal-codes-json-xml-csv/blob/master/data/DE.zip
        // visible to tests so they derive boundary inputs from these bounds instead of duplicating them
        internal const val MIN_POSTAL_CODE = 1067
        internal const val MAX_POSTAL_CODE = 99998

        // https://de.wikipedia.org/wiki/Hausnummer#Hausnummernerg%C3%A4nzungen
        private val HOUSE_NUMBER_PATTERN = Regex("\\d+[ \\-]?[a-zA-Z]?")
    }
}
