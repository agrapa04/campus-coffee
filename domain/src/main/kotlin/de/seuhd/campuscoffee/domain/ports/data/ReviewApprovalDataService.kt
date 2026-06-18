package de.seuhd.campuscoffee.domain.ports.data

import de.seuhd.campuscoffee.domain.exceptions.DuplicationException
import de.seuhd.campuscoffee.domain.model.objects.ReviewApproval

/**
 * Port for persisting review approvals (who approved which review).
 *
 * A port in the hexagonal architecture, defined by the domain and implemented by the data layer. The
 * starter ships the persistence; recording an approval here and deriving the approval count from it is
 * the subject of the assignment (Exercise 5).
 */
interface ReviewApprovalDataService {
    /**
     * Records that a user approved a review.
     *
     * @param approval the approval to persist
     * @return the persisted approval, with its generated id and timestamps
     * @throws DuplicationException if the same user already approved the same review
     */
    fun record(approval: ReviewApproval): ReviewApproval

    /**
     * Counts how many distinct users have approved a review.
     *
     * @param reviewId the review to count approvals for
     * @return the number of approvals
     */
    fun countByReviewId(reviewId: Long): Long

    /** Removes all approvals (used by the dev reset, mirroring the other data services' clear). */
    fun clear()
}
