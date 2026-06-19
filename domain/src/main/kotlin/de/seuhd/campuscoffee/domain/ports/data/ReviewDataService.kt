package de.seuhd.campuscoffee.domain.ports.data

import de.seuhd.campuscoffee.domain.model.objects.Pos
import de.seuhd.campuscoffee.domain.model.objects.Review
import de.seuhd.campuscoffee.domain.model.objects.User
import java.util.UUID

/**
 * Data service interface for review persistence operations.
 *
 * This is a port in the hexagonal architecture pattern, defined by the domain layer
 * and implemented by the data layer. Extends [CrudDataService] to inherit common CRUD
 * operations and adds review-specific operations.
 */
interface ReviewDataService : CrudDataService<Review, UUID> {
    /**
     * Retrieves all reviews for a specific point of sale that are approved/unapproved.
     *
     * @param pos      the point of sale to retrieve reviews for
     * @param approved the approval status to filter by
     * @return a list of all reviews for the specified point of sale
     */
    fun filter(
        pos: Pos,
        approved: Boolean
    ): List<Review>

    /**
     * Retrieves all reviews for a specific point of sale authored by a specific user.
     *
     * @param pos    the point of sale to retrieve reviews for
     * @param author the author whose reviews to retrieve
     * @return a list of reviews for the specified point of sale and author
     */
    fun filter(
        pos: Pos,
        author: User
    ): List<Review>
}
