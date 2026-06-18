package de.seuhd.campuscoffee.data.persistence.repositories

import de.seuhd.campuscoffee.data.persistence.entities.ReviewApprovalEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Repository for persisting review approval entities.
 */
interface ReviewApprovalRepository :
    JpaRepository<ReviewApprovalEntity, Long>,
    ResettableSequenceRepository {
    fun countByReviewId(reviewId: Long): Long
}
