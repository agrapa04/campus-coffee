package de.seuhd.campuscoffee.data.persistence.repositories

import de.seuhd.campuscoffee.data.persistence.entities.ReviewApprovalEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Repository for persisting review approval entities.
 */
interface ReviewApprovalRepository : JpaRepository<ReviewApprovalEntity, UUID> {
    fun countByReviewId(reviewId: UUID): Long
}
