package de.seuhd.campuscoffee.domain.ports.api

import de.seuhd.campuscoffee.domain.exceptions.ConcurrentUpdateException
import de.seuhd.campuscoffee.domain.exceptions.NotFoundException
import de.seuhd.campuscoffee.domain.model.objects.DomainModel
import java.util.UUID

/** Enumerates the entity types that support event-sourced revert operations. */
enum class EntityType {
    POS,
    USER,
    REVIEW
}

/**
 * Domain port for reverting the last recorded change of an entity via compensating events.
 * Implemented in the data module's RevertService, consumed by the API layer's controllers.
 *
 * A compensating event is appended to the log without removing any entry: reverting an INSERT
 * appends a DELETE, reverting an UPDATE appends an UPDATE restoring the previous state, and
 * reverting a DELETE appends an INSERT re-creating the entity.
 *
 * Optimistic locking prevents stale reverts: the client must pass the observed version, and a
 * mismatch is rejected as a conflict.
 */
interface RevertPort {
    /**
     * Reverts the last change recorded for the entity identified by [entityType] and [entityId].
     *
     * @param entityType      the type of entity to revert (POS, USER, or REVIEW)
     * @param entityId        the id of the entity to revert
     * @param observedVersion the version the caller observed; must match the current version
     * @return the restored domain object after revert, or null when the revert deleted the entity
     * @throws NotFoundException if no entity with the given id exists
     * @throws ConcurrentUpdateException if [observedVersion] does not match the current version
     */
    @Throws(NotFoundException::class, ConcurrentUpdateException::class)
    fun revertEntity(
        entityType: EntityType,
        entityId: UUID,
        observedVersion: Long
    ): DomainModel<*>?
}
