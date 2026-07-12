package de.seuhd.campuscoffee.data.implementations

import de.seuhd.campuscoffee.data.mapper.PosEntityMapper
import de.seuhd.campuscoffee.data.mapper.ReviewEntityMapper
import de.seuhd.campuscoffee.data.mapper.UserEntityMapper
import de.seuhd.campuscoffee.data.persistence.entities.Entity
import de.seuhd.campuscoffee.data.persistence.entities.PosEntity
import de.seuhd.campuscoffee.data.persistence.entities.ReviewEntity
import de.seuhd.campuscoffee.data.persistence.entities.UserEntity
import de.seuhd.campuscoffee.data.persistence.eventsourcing.ChangeType
import de.seuhd.campuscoffee.data.persistence.eventsourcing.EventStore
import de.seuhd.campuscoffee.data.persistence.eventsourcing.ReadModelProjector
import de.seuhd.campuscoffee.data.persistence.repositories.PosRepository
import de.seuhd.campuscoffee.data.persistence.repositories.ReviewRepository
import de.seuhd.campuscoffee.data.persistence.repositories.UserRepository
import de.seuhd.campuscoffee.domain.exceptions.ConcurrentUpdateException
import de.seuhd.campuscoffee.domain.exceptions.NotFoundException
import de.seuhd.campuscoffee.domain.model.objects.DomainModel
import de.seuhd.campuscoffee.domain.model.objects.Pos
import de.seuhd.campuscoffee.domain.model.objects.Review
import de.seuhd.campuscoffee.domain.model.objects.User
import de.seuhd.campuscoffee.domain.ports.api.EntityType
import de.seuhd.campuscoffee.domain.ports.api.RevertPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Implements the revert feature for event-sourced entities. A revert appends a compensating
 * event to the log (never removes entries), then projects it onto the read model atomically.
 *
 * Compensating events by last recorded change type:
 * - INSERT  -> DELETE (reverting a creation removes the entity)
 * - UPDATE  -> UPDATE with the previous event's body (reverting an update restores prior state)
 * - DELETE  -> INSERT with the previous event's body (reverting a deletion re-creates the entity)
 *
 * Optimistic locking prevents stale reverts: the caller's observed version must match the
 * read model's current version; a mismatch is rejected as a 409 conflict.
 */
@Service
@Transactional
class RevertService(
    private val eventStore: EventStore,
    private val readModelProjector: ReadModelProjector,
    private val posRepository: PosRepository,
    private val userRepository: UserRepository,
    private val reviewRepository: ReviewRepository,
    private val posMapper: PosEntityMapper,
    private val userMapper: UserEntityMapper,
    private val reviewMapper: ReviewEntityMapper
) : RevertPort {
    /**
     * Reverts the last recorded change for the given entity. Looks up the entity's event history,
     * determines the appropriate compensating event, and projects it atomically.
     */
    override fun revertEntity(
        entityType: EntityType,
        entityId: UUID,
        observedVersion: Long
    ): DomainModel<*>? {
        val label = entityTypeLabel(entityType)
        val domainClass = domainClassOf(entityType)

        // Retrieve the last events for this entity to determine what compensating event is needed
        val lastEvents = eventStore.getLastEvents(label, entityId, 2)
        if (lastEvents.isEmpty()) {
            throw NotFoundException(domainClass, entityId)
        }
        val lastEvent = lastEvents[0]

        // Load the JPA entity from the read model (null when last event is DELETE)
        val jpaEntity = loadJpaEntity(entityType, entityId)

        // Optimistic-locking guard: only enforce when the entity still exists in the read model
        if (jpaEntity != null) {
            val currentVersion = extractVersion(jpaEntity)
            if (currentVersion != observedVersion) {
                throw ConcurrentUpdateException(domainClass, entityId)
            }
        }

        // Dispatch on the last event's change type to determine the compensating event
        return when (lastEvent.changeType!!) {
            ChangeType.INSERT -> {
                // Reverting a creation: append a compensating DELETE to remove the entity
                val event = eventStore.appendDelete(domainClassOf(entityType).kotlin, entityId)
                readModelProjector.apply(event)
                null
            }
            ChangeType.UPDATE -> {
                // Reverting an update: restore the entity to its previous state via compensating UPDATE
                val previousEvent =
                    lastEvents.getOrNull(1)
                        ?: error("No previous event found to revert UPDATE for entity $entityId")
                val body = requireNotNull(previousEvent.body) { "Previous event must carry a body" }
                val event = eventStore.append(ChangeType.UPDATE, label, body)
                readModelProjector.apply(event)
                val restored =
                    loadJpaEntity(entityType, entityId)
                        ?: error("Entity $entityId not found after reverting UPDATE")
                mapToDomain(entityType, restored)
            }
            ChangeType.DELETE -> {
                // Reverting a deletion: re-insert the entity via compensating INSERT from prior state
                val previousEvent =
                    lastEvents.getOrNull(1)
                        ?: error("No previous event found to revert DELETE for entity $entityId")
                val body = requireNotNull(previousEvent.body) { "Previous event must carry a body" }
                val event = eventStore.append(ChangeType.INSERT, label, body)
                readModelProjector.apply(event)
                val restored =
                    loadJpaEntity(entityType, entityId)
                        ?: error("Entity $entityId not found after reverting DELETE")
                mapToDomain(entityType, restored)
            }
        }
    }

    /** Maps an EntityType enum to the event-log entity type label used by EventStore and ReadModelProjector. */
    private fun entityTypeLabel(type: EntityType): String =
        when (type) {
            EntityType.POS -> "Pos"
            EntityType.USER -> "User"
            EntityType.REVIEW -> "Review"
        }

    /** Returns the domain class for an EntityType, used when constructing NotFoundException or ConcurrentUpdateException. */
    private fun domainClassOf(type: EntityType): Class<out DomainModel<*>> =
        when (type) {
            EntityType.POS -> Pos::class.java
            EntityType.USER -> User::class.java
            EntityType.REVIEW -> Review::class.java
        }

    /** Loads the JPA entity from the appropriate repository, or null if it does not exist. */
    private fun loadJpaEntity(
        type: EntityType,
        id: UUID
    ): Entity? =
        when (type) {
            EntityType.POS -> posRepository.findById(id).orElse(null)
            EntityType.USER -> userRepository.findById(id).orElse(null)
            EntityType.REVIEW -> reviewRepository.findById(id).orElse(null)
        }

    /** Extracts the optimistic-locking version from a JPA entity regardless of its concrete type. */
    private fun extractVersion(entity: Entity): Long? =
        when (entity) {
            is PosEntity -> entity.version
            is UserEntity -> entity.version
            is ReviewEntity -> entity.version
            else -> null
        }

    /** Converts a JPA entity to its domain model representation using the appropriate mapper. */
    private fun mapToDomain(
        type: EntityType,
        entity: Entity
    ): DomainModel<*> =
        when (type) {
            EntityType.POS -> posMapper.fromEntity(entity as PosEntity)
            EntityType.USER -> userMapper.fromEntity(entity as UserEntity)
            EntityType.REVIEW -> reviewMapper.fromEntity(entity as ReviewEntity)
        }
}
