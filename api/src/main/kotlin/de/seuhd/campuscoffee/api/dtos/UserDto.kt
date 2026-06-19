package de.seuhd.campuscoffee.api.dtos

import com.fasterxml.jackson.annotation.JsonProperty
import de.seuhd.campuscoffee.domain.model.objects.Role
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalDateTime
import java.util.UUID

/**
 * DTO for user metadata. Properties are nullable, so a request body that omits a field deserializes and
 * is then rejected by bean validation; the controller validates the DTO before it is mapped to a
 * [de.seuhd.campuscoffee.domain.model.objects.User].
 *
 * [password] is write-only: required when creating a user (at least 8 characters), optional on update,
 * where omitting it keeps the current password. No response serializes it (and the stored hash is never
 * exposed at all). [roles] appears in responses; in a request body it is ignored at registration (a new
 * account is always a plain USER) and can be changed only by an admin (both enforced in the domain).
 */
data class UserDto(
    override val id: UUID? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
    @field:NotNull
    @field:Size(min = 1, max = 255, message = "Login name must be between 1 and 255 characters long.")
    @field:Pattern(regexp = "\\w+", message = "Login name can only contain word characters: [a-zA-Z_0-9]+")
    val loginName: String?,
    @field:NotNull
    @field:Email
    // @Email alone admits addresses longer than the 254-character column, which would surface as a 500
    @field:Size(max = 254, message = "Email address must be at most 254 characters long.")
    val emailAddress: String?,
    @field:NotNull
    @field:Size(min = 1, max = 255, message = "First name must be between 1 and 255 characters long.")
    val firstName: String?,
    @field:NotNull
    @field:Size(min = 1, max = 255, message = "Last name must be between 1 and 255 characters long.")
    val lastName: String?,
    @field:NotNull(groups = [OnCreate::class], message = "Password is required.")
    @field:NotBlank(groups = [OnCreate::class], message = "Password must not be blank.")
    @field:Size(min = 8, max = 255, message = "Password must be between 8 and 255 characters long.")
    @field:JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    val password: String? = null,
    val roles: Set<Role>? = null
) : Dto<UUID>
