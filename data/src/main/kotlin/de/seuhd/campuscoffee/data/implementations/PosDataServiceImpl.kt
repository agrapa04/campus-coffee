package de.seuhd.campuscoffee.data.implementations

import de.seuhd.campuscoffee.data.constraints.ConstraintMapping
import de.seuhd.campuscoffee.data.mapper.PosEntityMapper
import de.seuhd.campuscoffee.data.persistence.entities.PosEntity
import de.seuhd.campuscoffee.data.persistence.repositories.PosRepository
import de.seuhd.campuscoffee.domain.exceptions.NotFoundException
import de.seuhd.campuscoffee.domain.model.objects.Pos
import de.seuhd.campuscoffee.domain.ports.IdGenerator
import de.seuhd.campuscoffee.domain.ports.data.PosDataService
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Data-layer adapter implementing the POS data service port. Responsible for persistence;
 * business logic lives in the domain service layer.
 */
@Service(PosDataServiceImpl.BEAN_NAME)
class PosDataServiceImpl(
    repository: PosRepository,
    entityMapper: PosEntityMapper,
    idGenerator: IdGenerator
) : CrudDataServiceImpl<Pos, PosEntity, PosRepository, UUID>(
        repository,
        entityMapper,
        Pos::class.java,
        // unique constraint on the POS name, reported as a DuplicationException on that field
        setOf(ConstraintMapping({ it.name }, PosEntity.NAME_COLUMN, PosEntity.NAME_UNIQUE_CONSTRAINT)),
        idGenerator
    ),
    PosDataService {
    /**
     * Retrieves a POS by its unique name.
     *
     * @throws NotFoundException if no POS exists with the given name
     */
    override fun getByName(name: String): Pos = findByFieldOrThrow({ repository.findByName(name) }, "name", name)

    companion object {
        /**
         * Spring bean name of this relational adapter. The event-sourcing decorator qualifies on it to wrap
         * this bean. Without the qualifier, Spring would select the `@Primary` decorator as its own
         * [PosDataService] delegate.
         */
        const val BEAN_NAME = "posDataServiceImpl"
    }
}
