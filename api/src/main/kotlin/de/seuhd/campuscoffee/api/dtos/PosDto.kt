package de.seuhd.campuscoffee.api.dtos

import de.seuhd.campuscoffee.domain.model.enums.CampusType
import de.seuhd.campuscoffee.domain.model.enums.PosType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

/**
 * DTO for POS metadata. Properties are nullable so a request body that omits a field deserializes and
 * is then rejected by bean validation; the controller validates the DTO before it is mapped to a [Pos].
 */
data class PosDto(
    override val id: Long? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,

    @field:Size(min = 1, max = 255, message = "Name must be between 1 and 255 characters long.")
    val name: String?,

    @field:NotBlank(message = "Description cannot be empty.")
    val description: String?,

    @field:NotNull
    val type: PosType?,

    @field:NotNull
    val campus: CampusType?,

    @field:NotBlank(message = "Street cannot be empty.")
    val street: String?,

    @field:NotNull
    @field:Size(min = 1, max = 255, message = "House number must be between 1 and 255 characters long.")
    val houseNumber: String?,

    @field:NotNull
    val postalCode: Int?,

    @field:NotNull
    @field:Size(min = 1, max = 255, message = "City must be between 1 and 255 characters long.")
    val city: String?,
) : Dto<Long> {

    // --- temporary bridges so the still-Java mapper and tests keep compiling; removed once they are Kotlin ---
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
        fun name(v: String?) = apply { name = v }
        fun description(v: String?) = apply { description = v }
        fun type(v: PosType?) = apply { type = v }
        fun campus(v: CampusType?) = apply { campus = v }
        fun street(v: String?) = apply { street = v }
        fun houseNumber(v: String?) = apply { houseNumber = v }
        fun postalCode(v: Int?) = apply { postalCode = v }
        fun city(v: String?) = apply { city = v }

        fun build() = PosDto(
            id, createdAt, updatedAt, name, description, type, campus, street, houseNumber, postalCode, city,
        )
    }

    companion object {
        @JvmStatic
        fun builder() = Builder()
    }
}
