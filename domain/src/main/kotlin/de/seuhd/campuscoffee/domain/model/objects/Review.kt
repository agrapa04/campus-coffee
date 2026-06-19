package de.seuhd.campuscoffee.domain.model.objects

import java.time.LocalDateTime
import java.util.UUID

/**
 * Immutable review domain model. A review is approved once it reaches a configurable number of
 * approvals; [approvalCount] and [approved] are maintained by the domain module.
 */
data class Review(
    override val id: UUID? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
    val pos: Pos,
    val author: User,
    val review: String,
    val approvalCount: Int,
    val approved: Boolean
) : DomainModel<UUID>
