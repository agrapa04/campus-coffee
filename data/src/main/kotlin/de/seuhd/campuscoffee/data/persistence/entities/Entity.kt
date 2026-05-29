package de.seuhd.campuscoffee.data.persistence.entities

import de.seuhd.campuscoffee.data.persistence.generators.CustomSequence
import jakarta.persistence.Column
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Base entity providing the id and the createdAt/updatedAt timestamps, which are set by the JPA
 * lifecycle callbacks at the actual database operation time.
 */
@MappedSuperclass
abstract class Entity {
    @field:Id
    @field:GeneratedValue
    @field:CustomSequence
    var id: Long? = null

    @field:Column(name = "created_at")
    var createdAt: LocalDateTime? = null

    @field:Column(name = "updated_at")
    var updatedAt: LocalDateTime? = null

    @PrePersist
    protected fun onCreate() {
        val now = LocalDateTime.now(ZoneId.of("UTC"))
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    protected fun onUpdate() {
        updatedAt = LocalDateTime.now(ZoneId.of("UTC"))
    }
}
