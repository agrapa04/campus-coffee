package de.seuhd.campuscoffee.data.persistence.repositories

import de.seuhd.campuscoffee.data.persistence.entities.PosEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Repository for persisting point-of-sale (POS) entities.
 */
interface PosRepository : JpaRepository<PosEntity, UUID> {
    /**
     * Returns the POS with the given name, or null if none matches.
     *
     * @param name the POS name to look up
     */
    fun findByName(name: String): PosEntity?
}
