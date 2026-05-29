package de.seuhd.campuscoffee.domain.ports.api

import de.seuhd.campuscoffee.domain.model.objects.Review
import de.seuhd.campuscoffee.domain.ports.data.ReviewDataService

/**
 * Service interface for review operations.
 *
 * This is a port in the hexagonal architecture pattern, implemented by the domain layer
 * and consumed by the API layer. It encapsulates business rules and orchestrates
 * data operations through the [ReviewDataService] port.
 *
 * Extends [CrudService] to inherit common CRUD operations and adds review-specific operations.
 */
interface ReviewService : CrudService<Review, Long> {
    /**
     * Filters reviews by point of sale and approval status.
     *
     * @param posId    unique identifier of the point of sale to filter reviews for
     * @param approved the approval status to filter by
     * @return a list of reviews matching the filter criteria
     */
    fun filter(
        posId: Long,
        approved: Boolean
    ): List<Review>

    /**
     * Approves a review on behalf of a user.
     * The approval count is incremented, and the review may be marked as approved
     * if the approval threshold is reached. The updated review is persisted and returned.
     *
     * @param reviewId unique identifier of the review to approve
     * @param userId   unique identifier of the user approving the review
     * @return the persisted review with the incremented approval count and updated approval status
     */
    fun approve(
        reviewId: Long,
        userId: Long
    ): Review
}
