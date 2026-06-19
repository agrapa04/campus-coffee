package de.seuhd.campuscoffee.domain.model.objects

import java.time.LocalDateTime
import java.util.UUID

/**
 * Immutable user domain model. Fields are validated in the API layer via the DTOs.
 *
 * The two password fields are deliberately asymmetric. [password] is the raw, plaintext password and is
 * only ever set on the way *in* (create/update); the domain hashes it via the `PasswordHasher` port and
 * never reads it back. [passwordHash] is the stored hash, populated when a user is *read* and never
 * serialized to a client. [roles] is the user's set of authorization roles.
 */
data class User(
    override val id: UUID? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
    val loginName: String,
    val emailAddress: String,
    val firstName: String,
    val lastName: String,
    val roles: Set<Role> = emptySet(),
    val passwordHash: String? = null,
    val password: String? = null
) : DomainModel<UUID>
