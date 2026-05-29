package de.seuhd.campuscoffee.api.dtos

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

/**
 * DTO for user metadata. Properties are nullable so a request body that omits a field deserializes and
 * is then rejected by bean validation; the controller validates the DTO before it is mapped to a [User].
 */
data class UserDto(
    override val id: Long? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
    @field:NotNull
    @field:Size(min = 1, max = 255, message = "Login name must be between 1 and 255 characters long.")
    @field:Pattern(regexp = "\\w+", message = "Login name can only contain word characters: [a-zA-Z_0-9]+")
    val loginName: String?,
    @field:NotNull
    @field:Email
    val emailAddress: String?,
    @field:NotNull
    @field:Size(min = 1, max = 255, message = "First name must be between 1 and 255 characters long.")
    val firstName: String?,
    @field:NotNull
    @field:Size(min = 1, max = 255, message = "Last name must be between 1 and 255 characters long.")
    val lastName: String?
) : Dto<Long>
