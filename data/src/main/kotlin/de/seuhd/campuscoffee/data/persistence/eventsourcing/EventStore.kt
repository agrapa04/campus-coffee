package de.seuhd.campuscoffee.data.persistence.eventsourcing

import de.seuhd.campuscoffee.domain.model.objects.DomainModel
import de.seuhd.campuscoffee.domain.ports.IdGenerator
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import tools.jackson.core.type.TypeReference
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlin.reflect.KClass

/**
 * Appends events to the log. In event-sourcing mode the log is the source of truth; projecting the event
 * onto the read tables is a separate step ([ReadModelProjector]) the caller runs in the same transaction.
 *
 * The body is the full JSON state of the domain object (INSERT/UPDATE), or just its id (DELETE), built with
 * [EventJsonMapper] so it matches the `jsonb` column. The domain object's own id is inside the body; the
 * event's own id comes from a dedicated [IdGenerator] (the [EVENT_ID_GENERATOR] bean) with its own seed,
 * separate from the entity-id generator.
 */
@Service
class EventStore(
    private val eventRepository: EventRepository,
    @param:Qualifier(EVENT_ID_GENERATOR) private val idGenerator: IdGenerator
) {
    /** Appends an INSERT event carrying the full state of a newly created domain object. */
    fun appendInsert(domain: DomainModel<*>): EventEntity =
        append(ChangeType.INSERT, entityTypeOf(domain), toBody(domain))

    /** Appends an UPDATE event carrying the full new state of a modified domain object. */
    fun appendUpdate(domain: DomainModel<*>): EventEntity =
        append(ChangeType.UPDATE, entityTypeOf(domain), toBody(domain))

    /** Appends a DELETE event carrying only the id of the removed domain object. */
    fun appendDelete(
        domainType: KClass<out DomainModel<*>>,
        id: UUID
    ): EventEntity = append(ChangeType.DELETE, entityTypeOf(domainType), mapOf("id" to id.toString()))

    /** Removes every event for the given domain type. Part of the per-type clear, alongside the read table. */
    fun clear(entityType: String) = eventRepository.deleteByEntityType(entityType)

    /** Whether the log already holds an event for the given domain type, so the import can skip that type. */
    fun hasEventsFor(entityType: String): Boolean = eventRepository.existsByEntityType(entityType)

    /** The event's entity-type label, the domain class's simple name (`Pos`, `User`, `Review`, ...). */
    fun entityTypeOf(domain: DomainModel<*>): String = entityTypeOf(domain::class)

    fun entityTypeOf(domainType: KClass<out DomainModel<*>>): String =
        requireNotNull(domainType.simpleName) { "A domain type used for an event must have a simple name." }

    private fun append(
        changeType: ChangeType,
        entityType: String,
        body: Map<String, Any?>
    ): EventEntity {
        val event =
            EventEntity().apply {
                id = idGenerator.newId()
                this.changeType = changeType
                this.entityType = entityType
                entityVersion = PAYLOAD_SCHEMA_VERSION
                this.body = body
                createdAt = LocalDateTime.now(UTC)
            }
        // flush the event before the projection runs; if the projection then fails, the transaction rolls
        // back the event together with the projection, so the log never keeps an invalid event
        return eventRepository.saveAndFlush(event)
    }

    private fun toBody(domain: DomainModel<*>): Map<String, Any?> =
        EventJsonMapper.instance.convertValue(domain, BODY_TYPE)

    companion object {
        /** Bean name of the dedicated generator for event ids. */
        const val EVENT_ID_GENERATOR = "eventIdGenerator"

        /** The event payload schema version recorded on every event; increment it if the body format changes. */
        const val PAYLOAD_SCHEMA_VERSION = 1L

        private val BODY_TYPE = object : TypeReference<Map<String, Any?>>() {}
        private val UTC = ZoneId.of("UTC")
    }
}
