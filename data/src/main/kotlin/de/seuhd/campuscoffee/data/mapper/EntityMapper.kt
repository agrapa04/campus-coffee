package de.seuhd.campuscoffee.data.mapper

import de.seuhd.campuscoffee.data.persistence.entities.Entity
import de.seuhd.campuscoffee.domain.model.objects.DomainModel
import org.mapstruct.MappingTarget

/**
 * Generic mapper interface for converting between domain models and JPA entities (bidirectional).
 * Part of the data-layer adapter in the hexagonal architecture.
 *
 * @param DOMAIN the domain model type
 * @param ENTITY the JPA entity type
 */
interface EntityMapper<DOMAIN : DomainModel<*>, ENTITY : Entity> {
    /** Converts a JPA entity to its domain model representation. */
    fun fromEntity(source: ENTITY): DOMAIN

    /** Converts a domain model object to its JPA entity representation. */
    fun toEntity(source: DOMAIN): ENTITY

    /**
     * Updates an existing JPA entity with data from the domain model. JPA-managed fields (id,
     * createdAt, updatedAt) are preserved.
     */
    fun updateEntity(
        source: DOMAIN,
        @MappingTarget target: ENTITY
    )
}
