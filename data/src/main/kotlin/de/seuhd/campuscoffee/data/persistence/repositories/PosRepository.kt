package de.seuhd.campuscoffee.data.persistence.repositories

import de.seuhd.campuscoffee.data.persistence.entities.PosEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

/**
 * Repository for persisting point-of-sale (POS) entities.
 */
interface PosRepository : JpaRepository<PosEntity, Long>, ResettableSequenceRepository {
    fun findByName(name: String): Optional<PosEntity>
}
