package de.seuhd.campuscoffee.data.persistence.eventsourcing
import de.seuhd.campuscoffee.data.configuration.PersistenceProperties
import de.seuhd.campuscoffee.data.implementations.PosDataServiceImpl
import de.seuhd.campuscoffee.domain.model.objects.Pos
import de.seuhd.campuscoffee.domain.ports.data.PosDataService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Event-sourcing POS data adapter, active only when `campus-coffee.persistence.mode` is `event-sourcing`.
 * It is a Decorator (the design pattern) around the relational [PosDataServiceImpl]: both are adapters for
 * the same domain `PosDataService` port, and this one wraps the other. The read methods and the `getByName`
 * query delegate straight to it (`by delegate`); the mutating methods write event-first via
 * [EventSourcedMutator]. Marked `@Primary` so the domain service binds to it instead of the relational
 * adapter when both beans are present.
 */
@Service
@Primary
@ConditionalOnProperty(
    name = [PersistenceProperties.MODE_PROPERTY],
    havingValue = PersistenceProperties.EVENT_SOURCING_MODE
)
class EventSourcedPosDataService(
    private val delegate: PosDataServiceImpl,
    private val mutator: EventSourcedMutator
) : PosDataService by delegate {
    @Transactional
    override fun upsert(domain: Pos): Pos =
        mutator.upsert(
            domain,
            delegate::getById,
            { id, now -> domain.copy(id = id, createdAt = now, updatedAt = now) },
            { existing, now -> domain.copy(createdAt = existing.createdAt, updatedAt = now) }
        )

    @Transactional
    override fun delete(id: UUID) = mutator.delete(Pos::class, id, delegate::getById)

    @Transactional
    override fun clear() = mutator.clear(Pos::class, delegate::clear)
}
