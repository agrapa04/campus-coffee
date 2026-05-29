package de.seuhd.campuscoffee.data.persistence.repositories

import de.seuhd.campuscoffee.data.persistence.entities.UserEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Repository for persisting user entities.
 */
interface UserRepository :
    JpaRepository<UserEntity, Long>,
    ResettableSequenceRepository {
    fun findByLoginName(loginName: String): UserEntity?
}
