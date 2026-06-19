package de.seuhd.campuscoffee.data.persistence.eventsourcing

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Repository for the append-only event log.
 */
interface EventRepository : JpaRepository<EventEntity, UUID> {
    /** All events in append order (by the monotonic [EventEntity.seq]), for replaying the whole log. */
    fun findAllByOrderBySeqAsc(): List<EventEntity>

    /** Whether the log already holds at least one event for the given domain type, so adoption can skip it. */
    fun existsByEntityType(entityType: String): Boolean

    /** Removes every event for the given domain type, when clearing that type's data. */
    fun deleteByEntityType(entityType: String)
}
