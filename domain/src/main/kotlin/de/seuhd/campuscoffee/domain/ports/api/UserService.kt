package de.seuhd.campuscoffee.domain.ports.api

import de.seuhd.campuscoffee.domain.exceptions.ForbiddenException
import de.seuhd.campuscoffee.domain.exceptions.NotFoundException
import de.seuhd.campuscoffee.domain.model.objects.User
import de.seuhd.campuscoffee.domain.ports.data.UserDataService
import java.util.UUID

/**
 * Service interface for user operations.
 *
 * This is a port in the hexagonal architecture pattern, implemented by the domain layer
 * and consumed by the API layer. It encapsulates business rules and orchestrates
 * data operations through the [UserDataService] port.
 *
 * Extends [CrudService] to inherit common CRUD operations and adds user-specific operations.
 */
interface UserService : CrudService<User, UUID> {
    /**
     * Retrieves a specific user by their unique login name. This overload resolves the authenticated
     * principal itself (turning a login name into a [User]) and is therefore not subject to the
     * self-or-admin read rule; use [getByLoginName] with an `actingUser` for client-facing lookups.
     *
     * @param loginName the unique login name of the user to retrieve
     * @return the user with the specified login name
     * @throws NotFoundException if no user exists with the given login name
     */
    fun getByLoginName(loginName: String): User

    /**
     * Retrieves a user by id on behalf of [actingUser]. User data is not public, so only the target user
     * themselves or an admin may read it (listing all users is admin-only and gated at the web layer;
     * this guards reading a single user).
     *
     * @param id         the id of the user to retrieve
     * @param actingUser the authenticated user attempting the read
     * @throws NotFoundException if no user exists with [id]
     * @throws ForbiddenException if [actingUser] is neither the target user nor an admin
     */
    fun getById(
        id: UUID,
        actingUser: User
    ): User

    /**
     * Retrieves a user by login name on behalf of [actingUser], with the same self-or-admin rule as the
     * id-based [getById].
     *
     * @param loginName  the login name of the user to retrieve
     * @param actingUser the authenticated user attempting the read
     * @throws NotFoundException if no user exists with [loginName]
     * @throws ForbiddenException if [actingUser] is neither the target user nor an admin
     */
    fun getByLoginName(
        loginName: String,
        actingUser: User
    ): User

    /**
     * Registers a new user. Registration always creates a plain [Role.USER][de.seuhd.campuscoffee.domain.model.objects.Role.USER]
     * account regardless of any roles in the request, so a client cannot self-assign a privileged role.
     *
     * @param user the user to register
     * @return the persisted user
     */
    fun register(user: User): User

    /**
     * Updates a user on behalf of [actingUser], enforcing the self-service and escalation rules: a user
     * may edit only their own account (an admin may edit anyone), and only an admin may change a user's
     * roles. A non-admin update keeps the target's existing roles, so nobody can promote themselves.
     *
     * @param user       the user to update
     * @param actingUser the authenticated user attempting the update
     * @return the persisted, updated user
     * @throws ForbiddenException if [actingUser] may neither edit the target nor change the roles they sent
     */
    fun update(
        user: User,
        actingUser: User
    ): User
}
