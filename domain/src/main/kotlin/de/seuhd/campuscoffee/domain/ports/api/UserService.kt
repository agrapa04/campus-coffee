package de.seuhd.campuscoffee.domain.ports.api

import de.seuhd.campuscoffee.domain.exceptions.NotFoundException
import de.seuhd.campuscoffee.domain.model.objects.User
import de.seuhd.campuscoffee.domain.ports.data.UserDataService

/**
 * Service interface for user operations.
 *
 * This is a port in the hexagonal architecture pattern, implemented by the domain layer
 * and consumed by the API layer. It encapsulates business rules and orchestrates
 * data operations through the [UserDataService] port.
 *
 * Extends [CrudService] to inherit common CRUD operations and adds user-specific operations.
 */
interface UserService : CrudService<User, Long> {
    /**
     * Retrieves a specific user by their unique login name.
     *
     * @param loginName the unique login name of the user to retrieve
     * @return the user with the specified login name
     * @throws NotFoundException if no user exists with the given login name
     */
    fun getByLoginName(loginName: String): User
}
