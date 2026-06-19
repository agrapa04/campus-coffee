package de.seuhd.campuscoffee.data.persistence.entities

import jakarta.persistence.Column
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PostLoad
import jakarta.persistence.PostPersist
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Transient
import org.springframework.data.domain.Persistable
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/**
 * Base class for the JPA entities. It holds the id and the createdAt / updatedAt timestamps. The JPA
 * lifecycle callbacks set the timestamps when the row is written, and the data layer assigns the id
 * (a [UUID] from the `IdGenerator`) before the insert.
 *
 * The id has no `@GeneratedValue`, so the entity tells Spring Data whether it is new by implementing
 * [Persistable]: [isNew] is true until the row is loaded or persisted. `repository.save()` can then
 * insert a new entity without first running a SELECT to check whether it already exists.
 *
 * The id is a private field, read and written through the `getId()`/`setId()` methods, because a Kotlin
 * property named `id` would compile to the same `getId()` method as the [Persistable] override and clash
 * with it.
 * Callers still write `entity.id`, because Kotlin exposes a Java interface's `getId()`/`setId()` as a
 * property.
 */
@MappedSuperclass
abstract class Entity : Persistable<UUID> {
    @field:Id
    @field:Column(name = "id")
    private var entityId: UUID? = null

    @field:Column(name = "created_at")
    var createdAt: LocalDateTime? = null

    @field:Column(name = "updated_at")
    var updatedAt: LocalDateTime? = null

    @field:Transient
    private var persisted = false

    override fun getId(): UUID? = entityId

    fun setId(value: UUID?) {
        entityId = value
    }

    override fun isNew(): Boolean = !persisted

    @PostLoad
    @PostPersist
    protected fun markPersisted() {
        persisted = true
    }

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
