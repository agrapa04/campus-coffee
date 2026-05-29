package de.seuhd.campuscoffee.data.persistence.repositories

import de.seuhd.campuscoffee.data.persistence.entities.PosEntity
import de.seuhd.campuscoffee.data.persistence.entities.ReviewEntity
import de.seuhd.campuscoffee.data.persistence.entities.UserEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Repository for persisting review entities.
 */
interface ReviewRepository : JpaRepository<ReviewEntity, Long>, ResettableSequenceRepository {
    fun findAllByPosAndApproved(pos: PosEntity, approved: Boolean): List<ReviewEntity>
    fun findAllByPosAndAuthor(pos: PosEntity, author: UserEntity): List<ReviewEntity>
}
