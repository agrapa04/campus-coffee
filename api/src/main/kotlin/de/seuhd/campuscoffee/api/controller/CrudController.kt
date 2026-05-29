package de.seuhd.campuscoffee.api.controller

import de.seuhd.campuscoffee.api.dtos.Dto
import de.seuhd.campuscoffee.api.mapper.DtoMapper
import de.seuhd.campuscoffee.domain.model.objects.DomainModel
import de.seuhd.campuscoffee.domain.ports.api.CrudService
import org.springframework.http.ResponseEntity
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.net.URI

/**
 * Abstract base controller providing common CRUD operations. Subclasses supply the service and
 * mapper via the abstract methods (template method pattern).
 *
 * @param DOMAIN the domain object type
 * @param DTO    the data transfer object type
 * @param ID     the type of the unique identifier (e.g., Long, UUID, String)
 */
abstract class CrudController<DOMAIN : DomainModel<ID>, DTO : Dto<ID>, ID : Any> {

    /** The service used for the CRUD operations the controller provides. */
    protected abstract fun service(): CrudService<DOMAIN, ID>

    /** The mapper used to convert between domain objects and DTOs. */
    protected abstract fun mapper(): DtoMapper<DOMAIN, DTO>

    /** Retrieves all resources. */
    open fun getAll(): ResponseEntity<List<DTO>> =
        ResponseEntity.ok(service().getAll().map { mapper().fromDomain(it) })

    /** Retrieves a single resource by ID. */
    open fun getById(id: ID): ResponseEntity<DTO> =
        ResponseEntity.ok(mapper().fromDomain(service().getById(id)))

    /** Creates a new resource and returns 201 Created with its location. */
    open fun create(dto: DTO): ResponseEntity<DTO> {
        val created = upsert(dto)
        return ResponseEntity.created(getLocation(created.id!!)).body(created)
    }

    /**
     * Updates an existing resource by ID.
     *
     * @throws IllegalArgumentException if the ID in the path does not match the ID in the DTO
     */
    open fun update(id: ID, dto: DTO): ResponseEntity<DTO> {
        require(id == dto.id) { "ID in path and body do not match." }
        return ResponseEntity.ok(upsert(dto))
    }

    /** Deletes a resource by ID and returns 204 No Content. */
    open fun delete(id: ID): ResponseEntity<Void> {
        service().delete(id)
        return ResponseEntity.noContent().build()
    }

    /** Upserts a resource: maps DTO to domain, calls the service, and maps the result back to a DTO. */
    protected fun upsert(dto: DTO): DTO =
        mapper().fromDomain(service().upsert(mapper().toDomain(dto)))

    /** Builds the location URI for a newly created resource, used in the 201 Created response. */
    protected fun getLocation(resourceId: ID): URI =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(resourceId)
            .toUri()
}
