package de.seuhd.campuscoffee.domain.ports.data

import de.seuhd.campuscoffee.domain.exceptions.NotFoundException
import de.seuhd.campuscoffee.domain.model.objects.Pos

/**
 * Port interface for POS data operations.
 *
 * This port is implemented by the data layer (adapter) and defines the contract
 * for persistence operations on Point of Sale entities. Extends the generic
 * [CrudDataService] to inherit common CRUD operations.
 */
interface PosDataService : CrudDataService<Pos, Long> {
    /**
     * Retrieves a single POS entity by its unique name and returns it as a domain object.
     *
     * @param name the name of the POS to retrieve
     * @return the POS entity with the specified name
     * @throws NotFoundException if no POS exists with the given name
     */
    fun getByName(name: String): Pos
}
