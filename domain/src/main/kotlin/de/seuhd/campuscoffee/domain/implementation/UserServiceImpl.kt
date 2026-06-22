package de.seuhd.campuscoffee.domain.implementation

import de.seuhd.campuscoffee.domain.exceptions.ForbiddenException
import de.seuhd.campuscoffee.domain.model.objects.Role
import de.seuhd.campuscoffee.domain.model.objects.User
import de.seuhd.campuscoffee.domain.model.objects.persistedId
import de.seuhd.campuscoffee.domain.ports.api.UserService
import de.seuhd.campuscoffee.domain.ports.data.CrudDataService
import de.seuhd.campuscoffee.domain.ports.data.PasswordHasher
import de.seuhd.campuscoffee.domain.ports.data.UserDataService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Implementation of the User service that handles business logic related to user entities.
 */
@Service
class UserServiceImpl(
    private val userDataService: UserDataService,
    private val passwordHasher: PasswordHasher
) : CrudServiceImpl<User, UUID>(User::class.java),
    UserService {
    override fun dataService(): CrudDataService<User, UUID> = userDataService

    /**
     * Normalizes the password before delegating to the generic upsert. A freshly supplied raw password is
     * hashed and the raw value dropped, so the plaintext is never persisted or read back. An update that
     * omits the password keeps the user's existing stored hash. The write-only password is never sent
     * back to be re-submitted, so an omitted one means "unchanged", not "clear it".
     */
    override fun upsert(domainObject: User): User {
        val raw = domainObject.password
        val id = domainObject.id
        val toUpsert =
            when {
                raw != null -> domainObject.copy(passwordHash = passwordHasher.hash(raw), password = null)
                id != null -> domainObject.copy(passwordHash = userDataService.getById(id).passwordHash)
                else -> domainObject
            }
        return super.upsert(toUpsert)
    }

    // @Transactional on the entry point the controller calls directly (not on the self-invoked upsert,
    // where a proxy never intercepts): role normalization and the write commit together.
    @Transactional
    override fun register(user: User): User {
        // registration always yields a plain USER. A client cannot self-assign a privileged role by
        // putting one in the body; only an admin grants roles, and only on an update (see [update]).
        log.info { "Registering new user '${user.loginName}' as a plain USER." }
        return upsert(user.copy(roles = setOf(Role.USER)))
    }

    @Transactional
    override fun update(
        user: User,
        actingUser: User
    ): User {
        val targetId = requireNotNull(user.id) { "A user update must carry the user id." }
        val isAdmin = Role.ADMIN in actingUser.roles
        val isSelf = actingUser.persistedId == targetId

        // self-service: a user may edit only their own account; an admin may edit anyone
        if (!isAdmin && !isSelf) {
            throw ForbiddenException("Only an admin may edit another user (ID '$targetId').")
        }

        // changing roles is an admin responsibility (an admin may set anyone's roles, including their own);
        // a non-admin update keeps the target's existing roles, so a plain user cannot promote themselves by
        // putting roles in the request body
        val existingRoles = userDataService.getById(targetId).roles
        val toUpsert =
            if (isAdmin) {
                // an admin may set roles; if the body omits them, keep the existing set. USER is the base
                // role every user always holds (see [Role]), so an admin can grant or revoke MODERATOR and
                // ADMIN but never strip USER — it is re-added here regardless of what the body requests.
                user.copy(roles = user.roles.ifEmpty { existingRoles } + Role.USER)
            } else {
                if (user.roles.isNotEmpty() && user.roles != existingRoles) {
                    throw ForbiddenException("Only an admin may change a user's roles.")
                }
                user.copy(roles = existingRoles)
            }
        return upsert(toUpsert)
    }

    override fun getByLoginName(loginName: String): User {
        log.debug { "Retrieving user with login name: $loginName" }
        return userDataService.getByLoginName(loginName)
    }

    override fun getById(
        id: UUID,
        actingUser: User
    ): User = userDataService.getById(id).also { requireMayView(it, actingUser) }

    override fun getByLoginName(
        loginName: String,
        actingUser: User
    ): User = getByLoginName(loginName).also { requireMayView(it, actingUser) }

    /**
     * Requires that [actingUser] may view [target]; user data (login name, email, roles) is not public, so
     * only the target user or an admin may read it.
     */
    private fun requireMayView(
        target: User,
        actingUser: User
    ) {
        val isAdmin = Role.ADMIN in actingUser.roles
        val isSelf = actingUser.persistedId == target.persistedId
        if (!isAdmin && !isSelf) {
            throw ForbiddenException("Only an admin may view another user (ID '${target.id}').")
        }
    }

    private companion object {
        private val log = KotlinLogging.logger {}
    }
}
