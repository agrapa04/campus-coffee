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
    val city: String?
) : Dto<Long>
