package de.seuhd.campuscoffee.data.implementations

import de.seuhd.campuscoffee.domain.ports.api.RevertPort
import de.seuhd.campuscoffee.domain.model.api.EntityType
import de.seuhd.campuscoffee.data.persistence.eventsourcing.EventStore
import de.seuhd.campuscoffee.data.persistence.eventsourcing.ReadModelProjector
import de.seuhd.campuscoffee.data.persistence.entities.PosRepository
import de.seuhd.campuscoffee.data.persistence.entities.UserRepository
import de.seuhd.campuscoffee.data.persistence.entities.ReviewRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional
class RevertService(
    private val eventStore: EventStore,
    private val readModelProjector: ReadModelProjector,
    private val posRepository: PosRepository,
    private val userRepository: UserRepository,
    private val reviewRepository: ReviewRepository
) : RevertPort {
    override fun revertEntity(entityType: EntityType, entityId: UUID, observedVersion: Long) {
        val entity = when (entityType) {
            EntityType.POS -> posRepository.findById(entityId).orElse(null)
            EntityType.USER -> userRepository.findById(entityId).orElse(null)
            EntityType.REVIEW -> reviewRepository.findById(entityId).orElse(null)
        } ?: throw NotFoundException("Entity of type $entityType with ID $entityId not found.")

        if (entity.version != observedVersion) {
            throw ForbiddenException("Observed version $observedVersion does not match current version ${entity.version}.")
        }

        val lastEvent = eventStore.getLastEvent(entityType, entityId)
            ?: throw NotFoundException("No events found for entity of type $entityType with ID $entityId.")

        val compensatingEvent = buildCompensatingEvent(entityType, entityId, lastEvent)
        eventStore.append(compensatingEvent)
        readModelProjector.apply(compensatingEvent)
    }
    private fun buildCompensatingEvent(entityType: EntityType, entityId: UUID, observedVersion: Long) : EventEntity {
        return when (lastEvent.changeType){
            ChangeType.INSERT -> EventEntity(entityType, entityId, ChangeType.DELETE, observedVersion + 1)
            ChangeType.UPDATE -> EventEntity(entityType, entityId, ChangeType.UPDATE, observedVersion + 1)
            ChangeType.DELETE -> EventEntity(entityType, entityId, ChangeType.INSERT, observedVersion + 1)
        }
    }
}
