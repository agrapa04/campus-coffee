package de.seuhd.campuscoffee.data.persistence.repositories

import de.seuhd.campuscoffee.data.persistence.entities.PosEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Repository for persisting point-of-sale (POS) entities.
 */
interface PosRepository : JpaRepository<PosEntity, UUID> {
    fun findByName(name: String): PosEntity?
}
