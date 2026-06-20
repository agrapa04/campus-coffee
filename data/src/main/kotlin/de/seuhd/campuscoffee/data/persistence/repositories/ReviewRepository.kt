package de.seuhd.campuscoffee.data.persistence.repositories

import de.seuhd.campuscoffee.data.persistence.entities.PosEntity
import de.seuhd.campuscoffee.data.persistence.entities.ReviewEntity
import de.seuhd.campuscoffee.data.persistence.entities.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Repository for persisting review entities.
 */
interface ReviewRepository : JpaRepository<ReviewEntity, UUID> {
    /**
     * Returns the reviews for the given POS that have the given approval state.
     *
     * @param pos the POS whose reviews are queried
     * @param approved whether to return approved or unapproved reviews
     */
    fun findAllByPosAndApproved(
        pos: PosEntity,
        approved: Boolean
    ): List<ReviewEntity>

    /**
     * Returns the reviews for the given POS written by the given author.
     *
     * @param pos the POS whose reviews are queried
     * @param author the author whose reviews are returned
     */
    fun findAllByPosAndAuthor(
        pos: PosEntity,
        author: UserEntity
    ): List<ReviewEntity>
}
