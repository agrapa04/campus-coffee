package de.seuhd.campuscoffee.domain.implementation

import de.seuhd.campuscoffee.domain.configuration.ApprovalConfiguration
import de.seuhd.campuscoffee.domain.exceptions.ValidationException
import de.seuhd.campuscoffee.domain.model.objects.Review
import de.seuhd.campuscoffee.domain.ports.api.ReviewService
import de.seuhd.campuscoffee.domain.ports.data.CrudDataService
import de.seuhd.campuscoffee.domain.ports.data.PosDataService
import de.seuhd.campuscoffee.domain.ports.data.ReviewDataService
import de.seuhd.campuscoffee.domain.ports.data.UserDataService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Implementation of the Review service that handles business logic related to review entities.
 */
@Service
class ReviewServiceImpl(
    private val reviewDataService: ReviewDataService,
    private val userDataService: UserDataService,
    private val posDataService: PosDataService,
    private val approvalConfiguration: ApprovalConfiguration,
) : CrudServiceImpl<Review, Long>(Review::class.java), ReviewService {

    override fun dataService(): CrudDataService<Review, Long> = reviewDataService

    @Transactional
    override fun upsert(domainObject: Review): Review {
        // validate that the POS exists before creating or updating the review
        val pos = posDataService.getById(domainObject.pos.id!!)

        // on creation, validate that this is the author's first review for this POS;
        // on update the filter would match the review being updated, so skip the check
        if (domainObject.id == null && reviewDataService.filter(pos, domainObject.author).isNotEmpty()) {
            throw ValidationException(
                "Author with ID '${domainObject.author.id}'" +
                    " has already reviewed POS with ID '${pos.id}'.",
            )
        }

        return super.upsert(domainObject)
    }

    @Transactional(readOnly = true)
    override fun filter(posId: Long, approved: Boolean): List<Review> =
        reviewDataService.filter(posDataService.getById(posId), approved)

    @Transactional
    override fun approve(reviewId: Long, userId: Long): Review {
        log.info(
            "Processing approval request for review with ID '{}' by user with ID '{}'...",
            reviewId, userId,
        )

        // validate that the user exists
        val user = userDataService.getById(userId)
        val approverId = user.id!!

        // validate that the review exists
        val reviewToApprove = reviewDataService.getById(reviewId)
        val authorId = reviewToApprove.author.id!!

        // a user cannot approve their own review
        if (authorId == approverId) {
            log.warn(
                "User with ID '{}' attempted to approve their own review with ID '{}'.",
                approverId, reviewId,
            )
            throw ValidationException(
                "User with ID '$approverId' cannot approve their own review with ID '$reviewId'.",
            )
        }

        // increment approval count on the freshly fetched review
        val approvedReview = reviewToApprove.copy(approvalCount = reviewToApprove.approvalCount + 1)

        // update approval status to determine if the review now reaches the approval quorum
        val finalReview = updateApprovalStatus(approvedReview)
        if (finalReview.approved) {
            log.info(
                "Review with ID '{}' has now reached the approval quorum ({}/{})",
                finalReview.id, finalReview.approvalCount, approvalConfiguration.minCount,
            )
        } else {
            log.info(
                "Review with ID '{}' has not reached the approval quorum ({}/{})",
                finalReview.id, finalReview.approvalCount, approvalConfiguration.minCount,
            )
        }

        return reviewDataService.upsert(finalReview)
    }

    /**
     * Calculates and updates the approval status of a review based on its approval count.
     * Business rule: a review is approved when it reaches the configured minimum approval count.
     */
    fun updateApprovalStatus(review: Review): Review {
        log.debug("Updating approval status of review with ID '{}'...", review.id)
        return review.copy(approved = isApproved(review))
    }

    /**
     * Determines whether a review meets the minimum approval threshold.
     */
    private fun isApproved(review: Review): Boolean =
        review.approvalCount >= approvalConfiguration.minCount!!

    private companion object {
        private val log = LoggerFactory.getLogger(ReviewServiceImpl::class.java)
    }
}
