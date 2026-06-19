package de.seuhd.campuscoffee.data.persistence.eventsourcing

import de.seuhd.campuscoffee.domain.model.objects.DomainModel
import de.seuhd.campuscoffee.domain.ports.IdGenerator
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlin.reflect.KClass

/**
 * The shared event-first logic the event-sourcing data-service decorators use for their mutating
 * operations (create, update, delete, clear). Each operation assigns the id and timestamps so the event
 * body is complete, appends the event, then projects it onto the read tables, all within the caller's
 * transaction (the decorator methods are `@Transactional`). If the projection violates a constraint it
 * throws and the whole transaction rolls back, so the log never keeps an invalid event.
 *
 * The decorators pass the per-type steps in as lambdas (how to build the domain object via `copy`, and how
 * to read one back), so this holds no per-type knowledge. Ids come from the primary [IdGenerator] (the one
 * the relational adapter also uses), so the assigned entity ids match the relational mode exactly.
 */
@Component
class EventSourcedMutator(
    private val eventStore: EventStore,
    private val projector: ReadModelProjector,
    private val idGenerator: IdGenerator
) {
    /**
     * Creates (no id) or updates (id present) a domain object. On create it assigns a new id and both
     * timestamps; on update it loads the current row (a missing one throws [NotFoundException]
     * [de.seuhd.campuscoffee.domain.exceptions.NotFoundException]), keeps its `createdAt`, and sets a new
     * `updatedAt`. Returns the projected row, read back through [getById].
     */
    fun <D : DomainModel<UUID>> upsert(
        domain: D,
        getById: (UUID) -> D,
        buildForInsert: (id: UUID, now: LocalDateTime) -> D,
        buildForUpdate: (existing: D, now: LocalDateTime) -> D
    ): D {
        val now = now()
        val existingId = domain.id
        val complete: D
        val event: EventEntity
        if (existingId == null) {
            complete = buildForInsert(idGenerator.newId(), now)
            event = eventStore.appendInsert(complete)
        } else {
            complete = buildForUpdate(getById(existingId), now)
            event = eventStore.appendUpdate(complete)
        }
        project(event)
        return getById(requireNotNull(complete.id) { "The built domain object must have an id." })
    }

    /**
     * Persists a newly created domain object for a port that has no read-by-id method (the review-approval
     * port): assigns a new id and the timestamps, appends the INSERT event, projects it, and returns the
     * created object.
     */
    fun <D : DomainModel<UUID>> create(buildForInsert: (id: UUID, now: LocalDateTime) -> D): D {
        val complete = buildForInsert(idGenerator.newId(), now())
        project(eventStore.appendInsert(complete))
        return complete
    }

    /**
     * Deletes a domain object. Loads it first (a missing one throws [NotFoundException]
     * [de.seuhd.campuscoffee.domain.exceptions.NotFoundException], matching the relational adapter), then
     * appends the DELETE event and projects the removal (a still-referenced row throws
     * [DeletionConflictException][de.seuhd.campuscoffee.domain.exceptions.DeletionConflictException]).
     */
    fun delete(
        domainType: KClass<out DomainModel<*>>,
        id: UUID,
        loadForExistence: (UUID) -> Any?
    ) {
        loadForExistence(id)
        project(eventStore.appendDelete(domainType, id))
    }

    /** Clears a type: removes its events and clears its read table (via the delegate's own clear). */
    fun clear(
        domainType: KClass<out DomainModel<*>>,
        clearReadModel: () -> Unit
    ) {
        eventStore.clear(eventStore.entityTypeOf(domainType))
        clearReadModel()
    }

    private fun project(event: EventEntity) = projector.apply(event)

    private fun now() = LocalDateTime.now(UTC)

    private companion object {
        private val UTC = ZoneId.of("UTC")
    }
}
