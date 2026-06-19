package de.seuhd.campuscoffee.domain.model.objects

import java.time.LocalDateTime
import java.util.UUID

/**
 * Immutable record of a single user's approval of a review. Backed by the `review_approvals` table whose
 * unique (review_id, user_id) constraint enforces "one approval per user per review".
 *
 * The approve workflow that records these is the subject of the assignment (Exercise 5); the starter
 * ships the model, the port, and the persistence so the seeding can stay consistent with each review's
 * approval count.
 */
data class ReviewApproval(
    override val id: UUID? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
    val reviewId: UUID,
    val userId: UUID
) : DomainModel<UUID>
