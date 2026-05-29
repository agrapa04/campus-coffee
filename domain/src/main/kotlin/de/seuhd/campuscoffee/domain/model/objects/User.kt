package de.seuhd.campuscoffee.domain.model.objects

import java.time.LocalDateTime

/**
 * Immutable user domain model. Fields are validated in the API layer via the DTOs.
 */
data class User(
    override val id: Long? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
    val loginName: String,
    val emailAddress: String,
    val firstName: String,
    val lastName: String
) : DomainModel<Long>
