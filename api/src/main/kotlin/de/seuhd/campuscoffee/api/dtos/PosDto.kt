package de.seuhd.campuscoffee.api.dtos

import de.seuhd.campuscoffee.domain.model.enums.CampusType
import de.seuhd.campuscoffee.domain.model.enums.PosType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalDateTime
import java.util.UUID

/**
 * DTO for POS metadata. Properties are nullable, so a request body that omits a field deserializes and
 * is then rejected by bean validation; the controller validates the DTO before it is mapped to a
 * [de.seuhd.campuscoffee.domain.model.objects.Pos].
 */
data class PosDto(
    override val id: UUID? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
    @field:NotBlank(message = "Name cannot be empty.")
    @field:Size(max = 255, message = "Name must be at most 255 characters long.")
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
    @field:Pattern(regexp = "\\d{5}", message = "Postal code must be a five-digit string (e.g., \"69117\").")
    val postalCode: String?,
    @field:NotNull
    @field:Size(min = 1, max = 255, message = "City must be between 1 and 255 characters long.")
    val city: String?
) : Dto<UUID>
