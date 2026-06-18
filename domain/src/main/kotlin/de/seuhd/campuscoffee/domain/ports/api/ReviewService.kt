package de.seuhd.campuscoffee.domain.ports.api

import de.seuhd.campuscoffee.domain.exceptions.ForbiddenException
import de.seuhd.campuscoffee.domain.model.objects.Review
import de.seuhd.campuscoffee.domain.model.objects.User
import de.seuhd.campuscoffee.domain.ports.data.ReviewDataService

/**
 * Service interface for review operations.
 *
 * This is a port in the hexagonal architecture pattern, implemented by the domain layer
 * and consumed by the API layer. It encapsulates business rules and orchestrates
 * data operations through the [ReviewDataService] port.
 *
 * Extends [CrudService] to inherit common CRUD operations and adds review-specific operations.
 * The acting [User] is passed in by the api layer (resolved from the authenticated principal) so the
 * ownership and moderation rules are decided here, in the domain, without any Spring Security types.
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
     * Creates a review authored by [actingUser]. The author is taken from the authenticated user, never
     * from the request body, so a client cannot post a review as someone else.
     *
     * @param review     the review to create (its author is overwritten with [actingUser])
     * @param actingUser the authenticated user creating the review
     * @return the persisted review
     */
    fun create(
        review: Review,
        actingUser: User
    ): Review

    /**
     * Updates a review on behalf of [actingUser]. Only the review's author may update it.
     *
     * @param review     the review to update
     * @param actingUser the authenticated user attempting the update
     * @return the persisted, updated review
     * @throws ForbiddenException if [actingUser] is not the review's author
     */
    fun update(
        review: Review,
        actingUser: User
    ): Review

    /**
     * Deletes a review on behalf of [actingUser]. Allowed for the review's author or any moderator.
     *
     * @param reviewId   unique identifier of the review to delete
     * @param actingUser the authenticated user attempting the deletion
     * @throws ForbiddenException if [actingUser] is neither the author nor a moderator
     */
    fun delete(
        reviewId: Long,
        actingUser: User
    )

    /**
     * Approves a review on behalf of [actingUser], recording the approver so a user can approve a review
     * at most once (a repeat is a 409 conflict). The approval count and approval status are derived from
     * the recorded approvals. The acting user may not approve their own review.
     *
     * @param reviewId   unique identifier of the review to approve
     * @param actingUser the authenticated user approving the review
     * @return the persisted review with the recomputed approval count and updated approval status
     */
    fun approve(
        reviewId: Long,
        actingUser: User
    ): Review
}
